"""Verify real wx.login reauthentication on a disposable loopback MySQL backend."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import socket
import subprocess
import time
import urllib.request

from local_backend import ROOT, TEST_KEYS, WX_KEYS, load_private


def ide(tool, action, **arguments):
    command = [os.environ['WECHAT_MCP_PYTHON'], os.environ['WECHAT_MCP_WRAPPER'], tool,
               json.dumps(dict(action=action, **arguments))]
    reply = subprocess.run(command, capture_output=True, text=True, check=True, timeout=90)
    envelope = json.loads(reply.stdout)
    if not envelope.get('success'):
        raise RuntimeError('IDE action failed: ' + action)
    return envelope['data']


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--practice', action='store_true', help='Exercise timer sessions using real WeChat login')
    args = parser.parse_args()
    if args.practice:
        script = 'smoke_timer_practice.js'
        name = 'practice-real'
        expected = dict(credited=True, retried=True, resumed=True, completed=True, history=True, stats=True, noCheckin=True)
    else:
        script = 'smoke_phone_reauth.js'
        name = 'account-real-reauth'
        expected = dict(sameAccount=True, phoneUnchanged=True, shortProof=True)
    db = load_private(ROOT.parent / '.tools/mysql-test.env', TEST_KEYS)
    if not db['TEST_DB_URL'].startswith('jdbc:mysql://127.0.0.1:33306/cxj_test?'):
        raise RuntimeError('This probe requires the isolated cxj_test schema')
    with socket.socket() as port:
        port.bind(('127.0.0.1', 18083))
    env = {key: os.environ[key] for key in ('PATH', 'HOME', 'LANG', 'TMPDIR') if key in os.environ}
    env.update(load_private(ROOT / '.env.wechat.local', WX_KEYS))
    env.update(DB_URL=db['TEST_DB_URL'], DB_USERNAME=db['TEST_DB_USERNAME'], DB_PASSWORD=db['TEST_DB_PASSWORD'],
               SERVER_PORT='18083', SPRING_PROFILES_ACTIVE='local,mysql', MEDIA_WORKER_ENABLED='false')
    java = next((ROOT.parent / '.tools').glob('jdk-*/Contents/Home/bin/java'))
    target = ROOT / 'server/target'
    jar = target / 'server-0.1.0-SNAPSHOT.jar'
    http = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    runtime_log = 'practice-real-runtime.log' if args.practice else 'account-real-runtime.log'
    with (target / runtime_log).open('w') as log:
        process = subprocess.Popen([str(java), '-Duser.timezone=UTC', '-jar', str(jar)], cwd=ROOT / 'server',
                                   stdout=log, stderr=log, env=env)
        try:
            for _ in range(100):
                if process.poll() is not None:
                    raise RuntimeError('Isolated backend failed to start')
                try:
                    with http.open('http://127.0.0.1:18083/actuator/health/readiness', timeout=1) as response:
                        if json.load(response).get('status') == 'UP':
                            break
                except OSError:
                    pass
                time.sleep(.3)
            else:
                raise RuntimeError('Readiness timeout')
            ide('wechat_ide', 'status')
            if not ide('wechat_automator', 'start').get('verified'):
                raise RuntimeError('IDE automator unverified')
            path = ide('wechat_automator', 'page_data', expected_path='pages/login/index').get('path')
            if path != 'pages/login/index':
                raise RuntimeError('Unexpected probe page')
            result = ide('wechat_automator', 'evaluate',
                         fn_source=(ROOT / 'scripts' / script).read_text()).get('result')
            if result != expected:
                raise RuntimeError(name + ' checks failed')
            evidence = dict(expected, logout=True, jarSha256=hashlib.sha256(jar.read_bytes()).hexdigest())
            (target / (name + '.json')).write_text(json.dumps(evidence, indent=2))
            print(name + ' and logout verified')
        finally:
            process.terminate()
            process.wait(timeout=20)


if __name__ == '__main__':
    main()
