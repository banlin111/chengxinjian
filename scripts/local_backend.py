#!/usr/bin/env python3
"""Explicit, allowlisted loading of private local config; no shell sourcing or secret output."""
import argparse
import os
from pathlib import Path
import stat
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
WX_KEYS = {'WX_APP_ID', 'WX_APP_SECRET'}
CLOUD_KEYS = {'CLOUD_PROVIDER','STORAGE_BUCKET','STORAGE_REGION','CLOUD_SECRET_ID','CLOUD_SECRET_KEY','FFMPEG_PATH'}
DB_KEYS = {'DB_URL','DB_USERNAME','DB_PASSWORD','SPRING_PROFILES_ACTIVE',
           'LOCAL_ADMIN_USERNAME','LOCAL_ADMIN_PASSWORD','LOCAL_CAPTCHA_TICKET'}
TEST_KEYS = {'TEST_DB_URL','TEST_DB_USERNAME','TEST_DB_PASSWORD','TEST_DB_DRIVER'}

def load_private(path, allowed):
    path = Path(path)
    flags = os.O_RDONLY | getattr(os, 'O_NOFOLLOW', 0)
    fd = os.open(path, flags)
    try:
        info = os.fstat(fd)
        if not stat.S_ISREG(info.st_mode) or info.st_uid != os.getuid() or info.st_mode & 0o077:
            raise ValueError('Private config must be owned by the current user and have mode 0600')
        with os.fdopen(fd, encoding='utf-8') as f:
            fd = None
            result = {}
            for line in f:
                line = line.strip()
                if not line or line.startswith('#'):
                    continue
                key, sep, value = line.partition('=')
                if not sep or key in result:
                    raise ValueError('Invalid or duplicate private config entry')
                if key in allowed:
                    if value[:1] in ('"', "'") and value[-1:] == value[:1]:
                        value = value[1:-1]
                    result[key] = value
            return result
    finally:
        if fd is not None:
            os.close(fd)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['run','smoke','test','check-config'])
    parser.add_argument('--database-env', type=Path)
    parser.add_argument('--wechat', action='store_true', help='Explicitly load ignored .env.wechat.local')
    parser.add_argument('--cloud', action='store_true', help='Explicitly load ignored .env.cloud.local (never for tests)')
    args = parser.parse_args()
    if args.cloud and args.action=='test':
        raise ValueError('Live cloud credentials cannot be loaded into tests')
    env = os.environ.copy()
    if args.database_env:
        env.update(load_private(args.database_env, TEST_KEYS if args.action=='test' else DB_KEYS))
    if args.wechat:
        env.update(load_private(ROOT/'.env.wechat.local', WX_KEYS))
    if args.cloud:
        env.update(load_private(ROOT/'.env.cloud.local', CLOUD_KEYS))
    if args.action=='test' and args.database_env:
        # This helper only authorizes the known disposable test schema, never a development/production database.
        if not env.get('TEST_DB_URL','').startswith('jdbc:mysql://127.0.0.1:33306/cxj_test?'):
            raise ValueError('Refusing a database outside the isolated local cxj_test schema')
    if 'JAVA_HOME' not in env:
        candidates = sorted((ROOT.parent/'.tools').glob('jdk-*/Contents/Home'))
        if candidates:
            env['JAVA_HOME'] = str(candidates[-1])
    if args.action=='check-config':
        import re
        print('WX_APP_ID format:', bool(re.fullmatch(r'wx[0-9a-fA-F]{16}',env.get('WX_APP_ID',''))))
        print('WX_APP_SECRET format:', bool(re.fullmatch(r'[0-9a-fA-F]{32}',env.get('WX_APP_SECRET',''))))
        return 0
    command = [sys.executable, str(ROOT/'scripts/smoke_backend.py')] if args.action=='smoke' else [str(ROOT/'scripts/backend.sh'),args.action]
    return subprocess.call(command,cwd=ROOT,env=env)

if __name__=='__main__':
    try:
        sys.exit(main())
    except (OSError,ValueError):
        # Do not print exception arguments: malformed input could itself contain secrets.
        print('Local configuration could not be loaded; check file ownership, mode 0600, syntax and target database.',file=sys.stderr)
        sys.exit(2)
