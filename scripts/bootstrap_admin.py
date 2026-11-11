#!/usr/bin/env python3
"""Initialize the first administrator offline; passwords travel only on a child-process pipe."""
import argparse
import getpass
import json
import os
from pathlib import Path
import subprocess
import sys
from local_backend import load_private, ROOT


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--database-env', type=Path, required=True)
    parser.add_argument('--jar', type=Path, default=ROOT/'server/target/server-0.1.0-SNAPSHOT.jar')
    args = parser.parse_args()
    if not sys.stdin.isatty():
        raise ValueError('Use an interactive terminal')
    env = load_private(args.database_env, {'DB_URL', 'DB_USERNAME', 'DB_PASSWORD'})
    username = input('Administrator username (3–64 ASCII characters): ').strip()
    name = input('Display name: ').strip()
    reason = input('Initialization reason/operator reference: ').strip()
    password = getpass.getpass('Password (12–64 characters, <=72 UTF-8 bytes): ')
    if password != getpass.getpass('Confirm password: '):
        raise ValueError('Passwords differ')
    candidates = sorted((ROOT.parent/'.tools').glob('jdk-*/Contents/Home/bin/java'))
    java = str(Path(os.environ['JAVA_HOME'])/'bin/java') if os.environ.get('JAVA_HOME') else str(candidates[-1]) if candidates else 'java'
    command = [java, '-Duser.timezone=UTC', '-Dloader.main=com.chengxinjian.auth.AdminBootstrapCommand',
               '-cp', str(args.jar.resolve()), 'org.springframework.boot.loader.launch.PropertiesLauncher', '--initialize-first-admin']
    payload = json.dumps(dict(username=username, displayName=name, password=password, reason=reason)).encode()
    # No inherited provider keys, JAVA_TOOL_OPTIONS or shell expansion.
    return subprocess.run(command, input=payload, env={**env, 'PATH': os.environ.get('PATH', '')}, check=False).returncode


if __name__ == '__main__':
    try:
        sys.exit(main())
    except (OSError, ValueError, EOFError):
        print('Initialization not started. Check private configuration, terminal input and password confirmation.', file=sys.stderr)
        sys.exit(2)
