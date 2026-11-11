#!/usr/bin/env python3
"""Opt-in packaged non-local bootstrap smoke. Destructively resets ONLY local cxj_test."""
import argparse
import json
import os
import secrets
import socket
import subprocess
import time
import urllib.request
import urllib.error
from pathlib import Path
from local_backend import ROOT, TEST_KEYS, load_private


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run-isolated-bootstrap', action='store_true', required=True)
    parser.parse_args()
    test=load_private(ROOT.parent/'.tools/mysql-test.env',TEST_KEYS)
    if not test['TEST_DB_URL'].startswith('jdbc:mysql://127.0.0.1:33306/cxj_test?'):
        raise ValueError('Only isolated local cxj_test is authorized')
    with socket.socket() as listener:
        listener.bind(('127.0.0.1',18083))
    mysql=next((ROOT.parent/'.tools').glob('mysql-*/bin/mysql'))
    java=next((ROOT.parent/'.tools').glob('jdk-*/Contents/Home/bin/java'))
    jar=ROOT/'server/target/server-0.1.0-SNAPSHOT.jar'
    env=dict(PATH=os.environ.get('PATH',''),DB_URL=test['TEST_DB_URL'],DB_USERNAME=test['TEST_DB_USERNAME'],DB_PASSWORD=test['TEST_DB_PASSWORD'],
             SPRING_PROFILES_ACTIVE='mysql,prod',SERVER_PORT='18083',SERVER_ADDRESS='127.0.0.1',MEDIA_WORKER_ENABLED='false')
    def sql(statement):
        result=subprocess.run([str(mysql),'--protocol=TCP','-h127.0.0.1','-P33306','-u'+test['TEST_DB_USERNAME'],'--batch','--skip-column-names','cxj_test'],
                              input=statement,text=True,env={**env,'MYSQL_PWD':test['TEST_DB_PASSWORD']},capture_output=True)
        if result.returncode:
            raise RuntimeError('Isolated database check failed')
        return result.stdout.strip()
    tables=sql('SHOW TABLES').splitlines()
    if any(not name.replace('_','').isalnum() for name in tables):
        raise ValueError('Unexpected table name')
    sql('SET FOREIGN_KEY_CHECKS=0;'+''.join('DROP TABLE `'+name+'`;' for name in tables)+'SET FOREIGN_KEY_CHECKS=1;')
    opener=urllib.request.build_opener(urllib.request.ProxyHandler({}))
    evidence=ROOT/'server/target'
    report={'environment':'packaged Java + native MySQL cxj_test, mysql/prod; no real captcha','checks':[]}
    def check(name, condition):
        if not condition:
            raise RuntimeError('Check failed: '+name)
        report['checks'].append(name)
    with (evidence/'stage6-bootstrap-runtime.log').open('w') as log:
        app=subprocess.Popen([str(java),'-jar',str(jar)],env=env,stdout=log,stderr=log)
        try:
            for attempt in range(60):
                try:
                    with opener.open('http://127.0.0.1:18083/actuator/health/readiness',timeout=2) as r:
                        if json.load(r).get('status')=='UP':break
                except (OSError,ValueError):pass
                if app.poll() is not None:raise RuntimeError('Application startup failed')
                time.sleep(.5)
            else:raise RuntimeError('Readiness timeout')
            check('non-local startup creates no administrator',sql('SELECT COUNT(*) FROM admin_account')=='0')
            payload=dict(username='bootstrap-'+secrets.token_hex(5),displayName='隔离初始化演练',password=secrets.token_urlsafe(24),reason='仅本机 cxj_test 验证')
            command=[str(java),'-Duser.timezone=UTC','-Dloader.main=com.chengxinjian.auth.AdminBootstrapCommand','-cp',str(jar),'org.springframework.boot.loader.launch.PropertiesLauncher','--initialize-first-admin']
            children=[subprocess.Popen(command,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,env=env) for _ in range(2)]
            for child in children:
                child.stdin.write(json.dumps(payload).encode());child.stdin.close();child.stdin=None
            results=[child.communicate(timeout=30) for child in children]
            check('concurrent initialization creates exactly one administrator',sorted(c.returncode for c in children)==[0,2] and sql('SELECT COUNT(*) FROM admin_account')=='1')
            check('password only stored as BCrypt',sql('SELECT password_hash FROM admin_account').startswith('$2'))
            check('initialization audit is unique',sql("SELECT COUNT(*) FROM audit_log WHERE action='admin.bootstrap.secure'")=='1')
            check('all current dictionary permissions assigned',sql("SELECT COUNT(*) FROM admin_role_permission WHERE role_id IN(SELECT role_id FROM admin_account_role)")==sql('SELECT COUNT(*) FROM permission'))
            check('command output does not expose password',all(payload['password'].encode() not in out+err for out,err in results))
            request=urllib.request.Request('http://127.0.0.1:18083/api/v1/admin/auth/login',data=json.dumps(dict(username=payload['username'],password=payload['password'],captchaTicket='not-a-real-ticket')).encode(),headers={'Content-Type':'application/json'})
            try:r=opener.open(request)
            except urllib.error.HTTPError as e:r=e
            check('real captcha remains mandatory',r.status==503 and json.load(r)['code']=='PROVIDER_UNAVAILABLE')
            sql('DELETE FROM admin_account_role;DELETE FROM admin_account;')
            result=subprocess.run(command,input=json.dumps(payload).encode(),env=env,capture_output=True,timeout=30)
            check('persistent bootstrap marker prevents reinitialization after account deletion',result.returncode==2 and sql('SELECT COUNT(*) FROM admin_account')=='0')
        finally:
            app.terminate()
            try:app.wait(timeout=15)
            except subprocess.TimeoutExpired:app.kill();app.wait()
    (evidence/'stage6-bootstrap-runtime.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
    print('Packaged non-local bootstrap checks passed:',len(report['checks']))


if __name__=='__main__':
    try:main()
    except (OSError,ValueError,RuntimeError,subprocess.SubprocessError):
        print('Packaged bootstrap smoke failed; inspect private evidence. No credentials printed.')
        raise SystemExit(1)
