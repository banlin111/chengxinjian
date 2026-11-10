#!/usr/bin/env python3
"""Generate ignored DevTools project config from the existing private local config."""
import json
from pathlib import Path
from local_backend import ROOT, WX_KEYS, load_private

def main():
    config=load_private(ROOT/'.env.wechat.local',WX_KEYS)
    project={'appid': config['WX_APP_ID'], 'projectname': 'chengxinjian-backend-login-probe',
             'compileType': 'miniprogram', 'miniprogramRoot': './',
             'setting': {'urlCheck': False, 'es6': True, 'enhance': True},
             'libVersion': 'latest'}
    path=ROOT/'tools/wechat-login-probe/project.config.json'
    path.write_text(json.dumps(project,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print('Prepared local WeChat login probe config (AppID not printed).')

if __name__=='__main__':
    main()
