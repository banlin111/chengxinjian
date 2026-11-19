"""Exercise reviewed audio through the existing opt-in isolated COS harness."""
import json
import subprocess
import time
import uuid

from local_backend import ROOT
from smoke_phone_reauth import ide


def exercise(api, upload, check, cover_bytes, ffmpeg, report, cleanup, grants, mix=False):
    key = lambda: str(uuid.uuid4())

    def create(resource, table, body):
        item = api('POST', '/admin/content/' + resource, body, key())
        cleanup.append((table, str(uuid.UUID(item['id']))))
        return item

    def review(asset, decision='approve'):
        latest = api('GET', '/assets/' + asset)
        api('PUT', '/admin/assets/' + asset + '/review',
            dict(decision=decision, version=latest['version'], reason='isolated synthetic audio check'))

    cover, _ = upload('cover.png', 'cover', 'image/png', cover_bytes)
    review(cover['id'])
    items = {}
    fixtures = [('meditation', 'meditations', 'sine=frequency=220:duration=3'),
                ('noise', 'noises', 'anoisesrc=color=white:duration=3:amplitude=0.1')]
    if mix:
        fixtures.append(('ambient', 'noises', 'anoisesrc=color=pink:duration=3:amplitude=0.1'))
    for label, resource, signal in fixtures:
        kind = 'noise' if resource=='noises' else 'meditation'
        data = subprocess.run([ffmpeg, '-v', 'error', '-f', 'lavfi', '-i', signal, '-ar', '22050', '-ac', '1',
                               '-codec:a', 'libmp3lame', '-b:a', '32k', '-f', 'mp3', 'pipe:1'],
                              capture_output=True, check=True, env={'LANG':'C'}, timeout=10).stdout
        check(kind + ' bounded synthetic fixture', 1000 < len(data) < 20000)
        asset, _ = upload(kind + '.mp3', kind + '_media', 'audio/mpeg', data)
        category = create('categories', 'content_category', dict(name=kind, kind=kind, sort=0, status='published'))
        body = dict(title='合成音频联调', coverAssetId=cover['id'], assetId=asset['id'], categoryId=category['id'],
                    status='published', accessPolicy='free' if label!='noise' else 'member')
        if kind=='meditation':
            body.update(summary='短音频开发验证', teacherId=None)
        api('POST', '/admin/content/'+resource, body, key(), expected=409)
        review(asset['id'])
        item = create(resource, 'practice_audio', body)
        check(kind + ' review preserves private audio', api('GET', '/assets/'+asset['id'])['publicUrl'] is None)
        items[label] = (item['id'], asset['id'])

    ide('wechat_ide', 'status')
    check('real IDE and independent login project', ide('wechat_automator', 'start').get('verified') and
          ide('wechat_automator', 'page_data', expected_path='pages/login/index').get('path')=='pages/login/index')
    source = (ROOT / 'scripts/smoke_practice_audio.js').read_text()
    mix_source = (ROOT/'scripts/smoke_mix_preset.js').read_text() if mix else 'null'
    def run(mode, playback=None):
        arguments = [items['meditation'][0], items['noise'][0], mode, playback]
        start = """function(...args){const p=getCurrentPages().slice(-1)[0];p.audioProbe=null;
            (EXERCISE)(...args,MIX_FUNCTION).then(v=>p.audioProbe=v,()=>p.audioProbe={failed:'LOGOUT'});return true}"""
        ide('wechat_automator','evaluate',fn_source=start.replace('EXERCISE', source).replace('MIX_FUNCTION', mix_source),args_json=json.dumps(arguments))
        deadline=time.monotonic()+50
        while time.monotonic()<deadline:
            result=ide('wechat_automator','evaluate',fn_source='function(){return getCurrentPages().slice(-1)[0].audioProbe}').get('result')
            if result:
                if result.get('failed'):
                    raise RuntimeError('Native audio check failed: '+result['failed'])
                return result
            time.sleep(.4)
        raise RuntimeError('Native audio probe timed out')

    guided = run('guided')
    plan = create('membership-plans','membership_plan',dict(name='开发限定计划',period='month',durationDays=1,
                  originalPriceFen=None,priceFen=1,benefits=['仅开发验证'],status='published'))
    grant = api('POST','/admin/users/'+str(uuid.UUID(guided['userId']))+'/grants',
                dict(kind='membership',targetId=plan['id'],days=1,reason='isolated audio probe'),key())
    grants.append(str(uuid.UUID(grant['id'])))
    if mix:
        tracks = [dict(noiseId=items['ambient'][0],volume=0.35), dict(noiseId=items['noise'][0],volume=0.7)]
        public = create('mix-presets','mix_preset',dict(title='合成混音',tracks=tracks,status='published'))
        info = dict(editorial=public['id'],tracks=tracks)
        saved = run('save',info)
        cleanup.append(('mix_preset',str(uuid.UUID(saved['id']))))
        report['mix'] = run('mix', dict(info,saved=saved['id']))
        check('saved mix restored after real login and overlapping streams settled once', report['mix']['restored']
              and report['mix']['practiceSeconds'] == report['mix']['statsDelta'])
    noise = run('noise')
    for name, result in [('guided',guided),('noise',noise)]:
        check(name+' real native ended and credited once',result['nativeEnded'] and result['downloadStatus']==200
              and result['seconds']==result['statsDelta'] and result['seconds']>0)
        report[name]={k:result[k] for k in ('seconds','statsDelta','lastTime','nativeEnded','access','downloadStatus')}
    check('cross-login identity', guided['userId']==noise['userId'])
    api('POST','/admin/grants/'+grant['id']+'/revoke',dict(reason='end isolated audio probe'),key())
    review(items['meditation'][1], 'reject')
    check('membership revoked and narration withdrawn', run('revoked',noise['playbackId'])=={'revoked':True})
