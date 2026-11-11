#!/usr/bin/env python3
"""Pull and verify automatic staging backups via the existing pinned SSH configuration."""
import argparse
import datetime as dt
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import tempfile
import time
from staging_maintenance import atomic_json, checksum


def main():
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ssh-config', type=Path, default=root/'server/.local/staging-access/ssh_config')
    parser.add_argument('--directory', type=Path, default=root/'server/.local/backups/automatic')
    parser.add_argument('--retention-days', type=int, default=30)
    args = parser.parse_args()
    os.umask(0o077)
    info = args.ssh_config.lstat()
    if not stat.S_ISREG(info.st_mode) or info.st_uid != os.getuid() or info.st_mode & 0o077 or not 2 <= args.retention_days <= 365:
        raise ValueError('Invalid private SSH configuration or retention')
    args.directory.mkdir(mode=0o700, parents=True, exist_ok=True)
    if args.directory.is_symlink() or args.directory.stat().st_uid != os.getuid() or args.directory.stat().st_mode & 0o077:
        raise ValueError('Invalid backup directory')
    ssh = ['ssh','-F',str(args.ssh_config),'-o','BatchMode=yes','-o','ConnectTimeout=10','cxj-staging']
    # Fixed server-side source; no host/key material is printed or copied to a frontend.
    listing = subprocess.run(ssh+['sudo -n find /var/backups/cxj -maxdepth 1 -type f -name "cxj-auto-*.sql.gz.json" -printf "%f\\n"'],
                             check=True, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, timeout=30).stdout.decode().splitlines()
    verified = 0
    for name in listing:
        if not re.fullmatch(r'cxj-auto-[0-9]{8}T[0-9]{12}Z\.sql\.gz\.json', name):
            raise ValueError('Invalid manifest filename')
        manifest = json.loads(subprocess.run(ssh+['sudo -n cat /var/backups/cxj/'+name], check=True, stdout=subprocess.PIPE,
                                            stderr=subprocess.DEVNULL, timeout=30).stdout)
        filename = name[:-5]
        if manifest['file'] != filename or not re.fullmatch('[a-f0-9]{64}', manifest['sha256']):
            raise ValueError('Invalid manifest')
        target = args.directory/filename
        if target.is_symlink():
            raise ValueError('Backup symlink refused')
        if not target.exists():
            with tempfile.NamedTemporaryFile(dir=args.directory, prefix='.download-') as f:
                subprocess.run(ssh+['sudo -n cat /var/backups/cxj/'+filename], check=True, stdout=f, stderr=subprocess.DEVNULL, timeout=900)
                f.flush()
                os.fsync(f.fileno())
                if checksum(Path(f.name)) != manifest['sha256']:
                    raise ValueError('Backup checksum mismatch')
                os.link(f.name, target)
        if checksum(target) != manifest['sha256']:
            raise ValueError('Existing backup checksum mismatch')
        atomic_json(target.with_suffix(target.suffix+'.json'), manifest)
        verified += 1
    if not verified:
        raise ValueError('No remote automatic backups')
    files = sorted(args.directory.glob('cxj-auto-*.sql.gz'), reverse=True)
    for old in files[2:]:
        manifest_path = old.with_suffix(old.suffix+'.json')
        manifest = json.loads(manifest_path.read_text())
        if time.time()-manifest['completedAt'] > args.retention_days*86400 and checksum(old) == manifest['sha256']:
            old.unlink()
            manifest_path.unlink()
    newest = json.loads(files[0].with_suffix(files[0].suffix+'.json').read_text())
    if time.time()-newest['completedAt'] > 30*3600:
        raise ValueError('Remote backup is stale')
    atomic_json(args.directory/'last-sync.json', dict(status='ok', verified=verified, completedAt=int(time.time()), latest=newest['file']))
    print('Staging backup copies verified:', verified)


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, KeyError, subprocess.SubprocessError):
        print('Backup sync failed; inspect SSH connectivity, private directory and manifest integrity.')
        raise SystemExit(1)
