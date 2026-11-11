#!/usr/bin/env python3
"""Explicit real COS upload smoke, restricted to local disposable cxj_test and its own fixture objects."""
import argparse
import hashlib
import io
import json
import logging
import os
from pathlib import Path
import secrets
import struct
import subprocess
import sys
import time
import uuid
import wave
import zlib

from local_backend import ROOT, CLOUD_KEYS, WX_KEYS, TEST_KEYS, load_private


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run-real-cloud-upload', action='store_true', required=True)
    parser.add_argument('--course-probe', action='store_true', help='Exercise editorial media, a paid lesson and wx.login in the local IDE')
    args = parser.parse_args()
    logging.disable(logging.CRITICAL)
    import requests
    from qcloud_cos import CosConfig, CosS3Client
    from qcloud_cos.cos_exception import CosServiceError
    cloud = load_private(ROOT / '.env.cloud.local', CLOUD_KEYS)
    test = load_private(ROOT.parent / '.tools/mysql-test.env', TEST_KEYS)
    if not test.get('TEST_DB_URL', '').startswith('jdbc:mysql://127.0.0.1:33306/cxj_test?'):
        raise ValueError('Only isolated cxj_test is authorized')
    evidence = ROOT / 'server/target' / ('course-cos-smoke' if args.course_probe else 'media-cos-smoke')
    evidence.mkdir(parents=True, exist_ok=True)
    mysql = next((ROOT.parent / '.tools').glob('mysql-*/bin/mysql'))

    def sql(statement):
        env = os.environ.copy()
        env['MYSQL_PWD'] = test['TEST_DB_PASSWORD']
        result = subprocess.run([str(mysql), '--protocol=TCP', '-h127.0.0.1', '-P33306',
                                 '-u' + test['TEST_DB_USERNAME'], 'cxj_test'],
                                input=statement, text=True, env=env, capture_output=True)
        if result.returncode:
            raise RuntimeError('Isolated fixture database operation failed')

    for permission in ('content:read', 'content:write'):
        sql("INSERT INTO admin_role_permission(role_id,permission_code) SELECT 'role-content-manager','" + permission +
            "' WHERE NOT EXISTS(SELECT 1 FROM admin_role_permission WHERE role_id='role-content-manager' AND permission_code='" + permission + "');")

    username = 'cloud-smoke-' + uuid.uuid4().hex[:12]
    password, captcha = secrets.token_urlsafe(24), secrets.token_urlsafe(24)
    env = os.environ.copy()
    env.update(cloud)
    if args.course_probe:
        env.update(load_private(ROOT / '.env.wechat.local', WX_KEYS))
    env.update(DB_URL=test['TEST_DB_URL'], DB_USERNAME=test['TEST_DB_USERNAME'], DB_PASSWORD=test['TEST_DB_PASSWORD'],
               SPRING_PROFILES_ACTIVE='local,mysql', SERVER_PORT='18082', LOCAL_ADMIN_USERNAME=username,
               LOCAL_ADMIN_PASSWORD=password, LOCAL_CAPTCHA_TICKET=captcha)
    java = next((ROOT.parent / '.tools').glob('jdk-*/Contents/Home/bin/java'))
    jar = ROOT / 'server/target/server-0.1.0-SNAPSHOT.jar'
    report = {'checks': [], 'fixture_cleanup': False, 'test_database': 'cxj_test',
              'jar_sha256': hashlib.sha256(jar.read_bytes()).hexdigest(), 'started_at_epoch': int(time.time())}
    created = []
    review_role = str(uuid.uuid4())
    derived_keys = []
    course_fixture = {}
    grants = []

    client = CosS3Client(CosConfig(Region=cloud['STORAGE_REGION'], SecretId=cloud['CLOUD_SECRET_ID'],
                                 SecretKey=cloud['CLOUD_SECRET_KEY'], Scheme='https', Timeout=10), retry=0)
    base, token = 'http://127.0.0.1:18082/api/v1', None

    def check(name, condition):
        if not condition:
            raise RuntimeError('Check failed: ' + name)
        report['checks'].append(name)

    def api(method, path, data=None, key=None, expected=200):
        headers = {}
        if token:
            headers['Authorization'] = 'Bearer ' + token
        if key:
            headers['Idempotency-Key'] = key
        response = requests.request(method, base + path, json=data, headers=headers, timeout=15)
        if response.status_code != expected:
            body = response.json()
            code = body.get('code', 'ERROR')
            if not isinstance(code, str) or not code.replace('_', '').isalnum():
                code = 'ERROR'
            raise RuntimeError('API check failed: ' + str(response.status_code) + '/' + code)
        return response.json().get('data')

    def png():
        def chunk(kind, data):
            return struct.pack('!I', len(data)) + kind + data + struct.pack('!I', zlib.crc32(kind + data) & 0xffffffff)
        return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('!IIBBBBB', 2, 2, 8, 2, 0, 0, 0)) + chunk(b'IDAT', zlib.compress((b'\0' + b'\0\x80\x80' * 2) * 2)) + chunk(b'IEND', b'')

    def upload(name, purpose, mime, payload, sha=None):
        body = dict(purpose=purpose, filename=name, mimeType=mime, sizeBytes=len(payload),
                    sha256=sha or hashlib.sha256(payload).hexdigest())
        key = str(uuid.uuid4())
        ticket = api('POST', '/uploads', body, key)
        check(name + ': idempotent ticket', api('POST', '/uploads', body, key) == ticket)
        asset_id = ticket['assetId']
        uuid.UUID(asset_id)
        created.append(asset_id)
        fields = ticket['formData']
        check(name + ': temporary credential only', fields['q-ak'] != cloud['CLOUD_SECRET_ID'] and
              cloud['CLOUD_SECRET_KEY'] not in json.dumps(ticket))
        response = requests.post(ticket['uploadUrl'], data=fields,
                                 files={'file': (name, payload, mime)}, timeout=30, allow_redirects=False)
        check(name + ': real COS POST 204', response.status_code == 204)
        complete_key = str(uuid.uuid4())
        initial = api('POST', '/uploads/' + asset_id + '/complete', {'sha256': body['sha256']}, complete_key)
        check(name + ': queued for server validation', initial['status'] == 'processing')
        deadline = time.monotonic() + 45
        while time.monotonic() < deadline:
            result = api('GET', '/assets/' + asset_id)
            if result['status'] in ('ready', 'rejected'):
                break
            time.sleep(.25)
        else:
            raise RuntimeError('Server validation did not reach terminal state')
        check(name + ': completion snapshot survives processing',
              api('POST', '/uploads/' + asset_id + '/complete', {'sha256': body['sha256']}, complete_key) == initial)
        return result, ticket

    log = (evidence / 'server.log').open('w')
    process = subprocess.Popen([str(java), '-Duser.timezone=UTC', '-jar', str(jar)], cwd=ROOT / 'server', env=env, stdout=log, stderr=subprocess.STDOUT)
    try:
        deadline = time.monotonic() + 60
        while time.monotonic() < deadline:
            if process.poll() is not None:
                raise RuntimeError('Temporary backend failed to start')
            try:
                response = requests.get('http://127.0.0.1:18082/actuator/health/readiness', timeout=1)
                if response.status_code == 200 and response.json()['status'] == 'UP':
                    break
            except requests.RequestException:
                pass
            time.sleep(.25)
        else:
            raise RuntimeError('Readiness timeout')
        report['checks'].append('readiness UP')
        login = api('POST', '/admin/auth/login', dict(username=username, password=password, captchaTicket=captcha))
        token = login['accessToken']
        image = png()
        result, image_ticket = upload('fixture.png', 'avatar', 'image/png', image)
        check('decoded image ready and private', result['status'] == 'ready' and result['publicUrl'] is None)

        tampered = dict(image_ticket['formData'])
        tampered['key'] += '-outside-scope'
        response = requests.post(image_ticket['uploadUrl'], data=tampered, files={'file': ('fixture.png', image, 'image/png')}, timeout=15)
        check('changed object key denied by COS', response.status_code == 403)
        public_acl = dict(image_ticket['formData'])
        public_acl['acl'] = 'public-read'
        response = requests.post(image_ticket['uploadUrl'], data=public_acl, files={'file': ('fixture.png', image, 'image/png')}, timeout=15)
        check('public ACL mutation denied by COS', response.status_code == 403)
        response = requests.post(image_ticket['uploadUrl'], data=image_ticket['formData'], files={'file': ('fixture.png', image + b'x', 'image/png')}, timeout=15)
        check('oversized payload denied by COS', response.status_code in (400, 403))

        preview = api('POST', '/admin/assets/' + result['id'] + '/preview')
        check('signed read uses temporary object-scoped identity', cloud['CLOUD_SECRET_ID'] not in preview['url'] and 'x-cos-security-token=' in preview['url'].lower())
        downloaded = requests.get(preview['url'], timeout=15)
        check('server-promoted image hash verified', downloaded.status_code == 200 and hashlib.sha256(downloaded.content).hexdigest() == hashlib.sha256(image).hexdigest())
        anonymous = requests.get(preview['url'].split('?', 1)[0], timeout=15)
        check('anonymous access denied', anonymous.status_code == 403)
        response = requests.post(image_ticket['uploadUrl'], data=image_ticket['formData'], files={'file': ('fixture.png', b'x' * len(image), 'image/png')}, timeout=15)
        check('reusable ticket touches isolation only', response.status_code == 204)
        downloaded = requests.get(preview['url'], timeout=15)
        check('validated image unaffected by later isolation overwrite', downloaded.content == image)

        source_image = evidence / 'fixture.png'
        source_image.write_bytes(image)
        for suffix, mime in (('jpg', 'image/jpeg'), ('webp', 'image/webp')):
            target = evidence / ('fixture.' + suffix)
            encoded = subprocess.run([cloud['FFMPEG_PATH'], '-y', '-hide_banner', '-loglevel', 'error',
                                      '-i', str(source_image), '-frames:v', '1', str(target)], capture_output=True)
            check('generated tiny ' + suffix + ' fixture', encoded.returncode == 0)
            result, _ = upload('fixture.' + suffix, 'avatar', mime, target.read_bytes())
            check(suffix + ': decoded image ready and private', result['status'] == 'ready' and result['publicUrl'] is None)

        buf = io.BytesIO()
        with wave.open(buf, 'wb') as audio:
            audio.setnchannels(1); audio.setsampwidth(2); audio.setframerate(16000); audio.writeframes(b'\0\0' * 16000)
        result, _ = upload('fixture.wav', 'meditation_media', 'audio/wav', buf.getvalue())
        check('audio fully decoded with duration in seconds', result['status'] == 'ready' and 1 <= result['durationSeconds'] <= 2 and result['publicUrl'] is None)
        video = evidence / 'fixture.mp4'
        encoded = subprocess.run([cloud['FFMPEG_PATH'], '-y', '-hide_banner', '-loglevel', 'error', '-f', 'lavfi', '-i',
                                  'color=c=black:s=160x160:r=10:d=' + ('4' if args.course_probe else '1'), '-c:v', 'libx264', '-pix_fmt', 'yuv420p', str(video)], capture_output=True)
        check('generated tiny synthetic video fixture', encoded.returncode == 0)
        result, _ = upload('fixture.mp4', 'course_media', 'video/mp4', video.read_bytes())
        check('video fully decoded with duration in seconds', result['status'] == 'ready' and result['durationSeconds'] in ((4, 5) if args.course_probe else (1, 2)) and result['publicUrl'] is None)

        if args.course_probe:
            # Grant review only to this run's temporary account in the disposable schema.
            sql("INSERT INTO admin_role(id,name) VALUES ('"+review_role+"','"+review_role+"');"
                "INSERT INTO admin_role_permission VALUES ('"+review_role+"','moderation:review');"
                "INSERT INTO admin_role_permission VALUES ('"+review_role+"','users:write');"
                "INSERT INTO admin_account_role SELECT id,'"+review_role+"' FROM admin_account WHERE username='"+username+"';")

            def review(asset, decision='approve'):
                uid = asset['id']
                latest = api('GET', '/assets/'+uid)
                api('PUT', '/admin/assets/'+uid+'/review', dict(decision=decision, version=latest['version'], reason='synthetic integration fixture'))
                return api('GET', '/assets/'+uid)

            def content(resource, body):
                row = api('POST','/admin/content/'+resource,body,str(uuid.uuid4()))
                course_fixture[resource] = str(uuid.UUID(row['id']))
                return row

            media = review(result)
            cover, _ = upload('cover.png','cover','image/png',image)
            cover = review(cover)
            avatar = review(api('GET','/assets/'+image_ticket['assetId']))
            check('approved cover delivers rendered JPEG', requests.get(cover['publicUrl'],timeout=10).content.startswith(bytes.fromhex('ffd8ff')))
            teacher = content('teachers', dict(name='测试讲师', avatarAssetId=avatar['id'], bio='', credentials=[], userId=None, status='published'))
            check('real uploaded teacher avatar displayed', teacher['avatarUrl']==avatar['publicUrl'] and requests.get(teacher['avatarUrl'],timeout=10).status_code==200)
            category = content('categories', dict(name='测试分类',kind='course',sort=0,status='published'))
            course = content('courses',dict(title='合成测试课程', summary='', description='', coverAssetId=cover['id'],categoryId=category['id'],
                teacherId=teacher['id'],difficulty='beginner',priceFen=1,originalPriceFen=None,accessPolicy='purchase',status='published'))
            derived_keys.append('trials/'+media['id']+'/1.mp4')
            lesson = content('lessons',dict(courseId=course['id'],title='合成课节',sort=1,assetId=media['id'],trialSeconds=1,status='published'))
            admin_token = token
            token = None
            deadline = time.monotonic()+65
            while time.monotonic()<deadline:
                directory = api('GET','/courses/'+course['id']+'/lessons')
                if directory['items'][0]['access']['reason']=='trial':
                    break
                time.sleep(.5)
            else:
                raise RuntimeError('Trial encoding did not finish')
            check('public directory excludes private asset', 'assetId' not in directory['items'][0])
            # Independently decode the private derivative and inspect the decoded FFmpeg output clock.
            trial = evidence / 'derived.mp4'
            client.get_object(Bucket=cloud['STORAGE_BUCKET'],Key=derived_keys[0])['Body'].get_stream_to_file(str(trial))
            decoded = subprocess.run([cloud['FFMPEG_PATH'],'-nostdin','-v','error','-i',str(trial),'-progress','pipe:1','-f','null','-'],
                                     capture_output=True,text=True,timeout=15,env={'LANG':'C'})
            times = [int(line.split('=')[1]) for line in decoded.stdout.splitlines() if line.startswith('out_time_us=')]
            check('four-second source clipped to at most one second of decoded output', decoded.returncode==0 and 0<max(times)<=1000000)
            runner = Path(os.environ['WECHAT_MCP_PYTHON'])
            wrapper = Path(os.environ['WECHAT_MCP_WRAPPER'])
            def ide(action, **params):
                process = subprocess.run([str(runner),str(wrapper),'wechat_automator',json.dumps(dict(action=action,**params))],
                                         capture_output=True,text=True,timeout=90)
                if process.returncode: raise RuntimeError('IDE bridge failed')
                reply = json.loads(process.stdout)
                if not reply.get('success'): raise RuntimeError('IDE action failed: '+action)
                return reply['data']
            check('IDE automator verified', ide('start').get('verified') is True)
            check('isolated probe page verified', ide('page_data',expected_path='pages/login/index')['path']=='pages/login/index')
            def playback(reason, seconds):
                probe = ide('evaluate',fn_source=(ROOT/'scripts/smoke_course_probe.js').read_text(),args_json=json.dumps([course['id'],lesson['id'],reason,seconds]))['result']
                check('IDE wx.login, '+reason+' COS download, seek and renewal',probe['reason']==reason and probe['seconds']==seconds
                      and probe['downloadStatus']==200 and probe['creditedSeconds']==0)
                check('IDE '+reason+' learning, favorites, likes and cross-session resume', probe.get('libraryVerified') is True and probe.get('crossSessionVerified') is True)
                return str(uuid.UUID(probe['userId']))

            uid = playback('trial',1)
            token = admin_token
            grant = api('POST','/admin/users/'+uid+'/grants',dict(kind='course',targetId=course['id'],lifetime=True,reason='synthetic course entitlement'),str(uuid.uuid4()))
            grants.append(str(uuid.UUID(grant['id'])))
            check('same WeChat user with manual course entitlement',playback('purchased',4)==uid)
            api('POST','/admin/grants/'+grant['id']+'/revoke',dict(reason='end synthetic grant'),str(uuid.uuid4()))
            check('revoked grant falls back to isolated trial',playback('trial',1)==uid)
            review(cover,'reject')
            check('withdrawn image no longer delivered', requests.get(cover['publicUrl'],timeout=5).status_code==404)
            token = None
            api('GET','/courses/'+course['id'],expected=404)
            token = admin_token

        result, _ = upload('wrong-hash.png', 'avatar', 'image/png', image, 'a' * 64)
        check('server rejects mismatched SHA256', result['status'] == 'rejected')
        result, _ = upload('spoofed.png', 'avatar', 'image/png', b'not a real PNG')
        check('server rejects spoofed MIME despite matching hash', result['status'] == 'rejected')
        result, _ = upload('broken.mp4', 'course_media', 'video/mp4', b'\0\0\0\x18ftypisom' + b'\0' * 32)
        check('server rejects undecodable video despite matching container magic and hash', result['status'] == 'rejected')
        api('POST', '/admin/auth/logout', {'refreshToken': login['refreshToken']})
        report['checks'].append('session logout')
        report['passed'] = True
    finally:
        process.terminate()
        try:
            process.wait(timeout=15)
        except subprocess.TimeoutExpired:
            process.kill(); process.wait()
        log.close()
        try:
            for asset_id in created:
                keys = ['quarantine/'+asset_id+'/source', 'validated/'+asset_id+'/source']
                keys.extend(key for key in derived_keys if key.startswith('trials/'+asset_id+'/'))
                for object_key in keys:
                    client.delete_object(Bucket=cloud['STORAGE_BUCKET'], Key=object_key)
                    try:
                        client.head_object(Bucket=cloud['STORAGE_BUCKET'], Key=object_key)
                    except CosServiceError as error:
                        if error.get_status_code() != 404:
                            raise RuntimeError('Fixture cleanup could not be verified')
                    else:
                        raise RuntimeError('Fixture object still exists after cleanup')
                # Only these disposable fixture rows in cxj_test are changed; no business database is accepted.
                sql("UPDATE media_asset SET status='rejected',public_url=NULL,failure_code='TEST_FIXTURE_CLEANED',quarantine_cleaned=TRUE,version=version+1 WHERE id='" + asset_id + "';")
            sql("UPDATE auth_session SET revoked=TRUE WHERE account_id IN (SELECT id FROM admin_account WHERE username='" + username + "');")
            sql("UPDATE admin_account SET enabled=FALSE WHERE username='" + username + "';")
            if args.course_probe:
                sql("DELETE FROM admin_account_role WHERE role_id='"+review_role+"';"
                    "DELETE FROM admin_role_permission WHERE role_id='"+review_role+"';DELETE FROM admin_role WHERE id='"+review_role+"';")
                for grant in grants:
                    sql("UPDATE entitlement_grant SET revoked_at=COALESCE(revoked_at,CURRENT_TIMESTAMP) WHERE source='admin' AND source_id='"+grant+"';")
                for resource, table in [('lessons','content_lesson'),('courses','content_course'),('teachers','content_teacher'),('categories','content_category')]:
                    if resource in course_fixture:
                        sql("UPDATE "+table+" SET status='archived' WHERE id='"+course_fixture[resource]+"';")
                for asset_id in created:
                    sql("DELETE FROM editorial_image WHERE asset_id='"+asset_id+"';")
                (evidence / 'derived.mp4').unlink(missing_ok=True)
            report['fixture_cleanup'] = True
        finally:
            (evidence / 'result.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'passed': report.get('passed', False), 'checks': len(report['checks']),
                      'fixture_cleanup': report['fixture_cleanup']}, ensure_ascii=False))


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        # Only our fixed check labels/status codes can reach output; never print provider exceptions.
        message = str(error) if type(error) is RuntimeError else type(error).__name__
        print('Real COS smoke failed: ' + message, file=sys.stderr)
        sys.exit(1)
