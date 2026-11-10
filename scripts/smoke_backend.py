"""Run the packaged backend against an isolated MySQL development database.

Requires Java 21, DB_URL/DB_USERNAME/DB_PASSWORD, LOCAL_ADMIN_PASSWORD and
LOCAL_CAPTCHA_TICKET. Never point at production; this creates and archives a category.
"""
import json
import os
from pathlib import Path
import subprocess
import time
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]
SERVER = ROOT / 'server'


def main():
    env = os.environ.copy()
    for key in ('DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'LOCAL_ADMIN_PASSWORD', 'LOCAL_CAPTCHA_TICKET'):
        if not env.get(key):
            raise SystemExit('Missing environment variable: ' + key)
    if not env['DB_URL'].startswith('jdbc:mysql:'):
        raise SystemExit('This smoke check requires MySQL')
    env['SPRING_PROFILES_ACTIVE'] = 'local,mysql'
    env['LOCAL_ADMIN_USERNAME'] = env.get('LOCAL_ADMIN_USERNAME', 'smoke-admin')
    env['SERVER_PORT'] = env.get('SMOKE_PORT', '18080')
    java = str(Path(env['JAVA_HOME']) / 'bin' / 'java') if env.get('JAVA_HOME') else 'java'
    base = 'http://127.0.0.1:' + env['SERVER_PORT']

    def request(path, method='GET', body=None, headers=None):
        h = {'Content-Type': 'application/json'}
        h.update(headers or {})
        req = urllib.request.Request(base + path, method=method, headers=h,
            data=json.dumps(body).encode('utf-8') if body is not None else None)
        with urllib.request.urlopen(req, timeout=5) as response:
            return json.load(response)

    with (SERVER / 'target/http-smoke.log').open('w') as log:
        process = subprocess.Popen([java, '-jar', 'target/server-0.1.0-SNAPSHOT.jar'], cwd=SERVER,
                                   env=env, stdout=log, stderr=subprocess.STDOUT)
        try:
            for _ in range(100):
                if process.poll() is not None:
                    raise RuntimeError('Application stopped; see server/target/http-smoke.log')
                try:
                    if request('/actuator/health/readiness')['status'] == 'UP':
                        break
                except (urllib.error.URLError, TimeoutError):
                    time.sleep(.2)
            else:
                raise RuntimeError('Application did not become healthy')
            auth = request('/api/v1/admin/auth/login', 'POST', {
                'username': env['LOCAL_ADMIN_USERNAME'], 'password': env['LOCAL_ADMIN_PASSWORD'],
                'captchaTicket': env['LOCAL_CAPTCHA_TICKET']})['data']
            headers = {'Authorization': 'Bearer ' + auth['accessToken'], 'Idempotency-Key': str(uuid.uuid4())}
            body = {'name': 'HTTP验证分类', 'kind': 'course', 'sort': 1, 'status': 'draft'}
            first = request('/api/v1/admin/content/categories', 'POST', body, headers)['data']
            repeat = request('/api/v1/admin/content/categories', 'POST', body, headers)['data']
            if first != repeat:
                raise AssertionError('Create replay must return original category')
            published = request('/api/v1/admin/content/categories/' + first['id'], 'PUT',
                                {**body, 'status': 'published', 'version': first['version']}, headers)['data']
            if not any(x['id'] == first['id'] for x in request('/api/v1/categories?kind=course')['data']):
                raise AssertionError('Published category missing from public catalog')
            request('/api/v1/admin/content/categories/' + first['id'], 'DELETE',
                    {'reason': '验证完成归档', 'version': published['version']}, headers)
            if any(x['id'] == first['id'] for x in request('/api/v1/categories?kind=course')['data']):
                raise AssertionError('Archived category is still publicly visible')
            # Teacher registry uses the same real packaged server/database and contract.
            teacher_headers = {**headers, 'Idempotency-Key': str(uuid.uuid4())}
            teacher_body = {'name': 'HTTP验证讲师', 'bio': '仅用于开发验收，完成后归档',
                            'credentials': [], 'avatarAssetId': None, 'userId': None, 'status': 'draft'}
            teacher = request('/api/v1/admin/content/teachers', 'POST', teacher_body, teacher_headers)['data']
            teacher_replay = request('/api/v1/admin/content/teachers', 'POST', teacher_body, teacher_headers)['data']
            if teacher != teacher_replay:
                raise AssertionError('Teacher create replay must return original snapshot')
            teacher = request('/api/v1/admin/content/teachers/' + teacher['id'], 'PUT',
                {**teacher_body, 'status': 'published', 'version': teacher['version']}, teacher_headers)['data']
            visible = request('/api/v1/teachers/' + teacher['id'])['data']
            if visible['id'] != teacher['id'] or 'userId' in visible or 'avatarAssetId' in visible:
                raise AssertionError('Public teacher response leaked admin-only fields')
            request('/api/v1/admin/content/teachers/' + teacher['id'], 'DELETE',
                {'reason': '验证完成归档', 'version': teacher['version']}, teacher_headers)
            request('/api/v1/admin/auth/logout', 'POST', {'refreshToken': auth['refreshToken']}, headers)
            print('Packaged backend + MySQL + HTTP workflow: PASS')
        finally:
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()


if __name__ == '__main__':
    main()
