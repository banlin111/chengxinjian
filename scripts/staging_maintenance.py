#!/usr/bin/env python3
"""Bounded staging backup/monitor jobs. No credentials, media URLs or personal data in reports."""
import argparse
import datetime as dt
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import time
import urllib.request


def atomic_json(path, value):
    with tempfile.NamedTemporaryFile(mode='w', dir=path.parent, delete=False) as f:
        temporary = Path(f.name)
        json.dump(value, f, ensure_ascii=False, indent=2)
        f.flush()
        os.fsync(f.fileno())
    os.replace(temporary, path)


def checksum(path):
    result = hashlib.sha256()
    with path.open('rb') as f:
        for chunk in iter(lambda: f.read(1024*1024), b''):
            result.update(chunk)
    return result.hexdigest()


def sql(container, statement):
    result = subprocess.run(['docker', 'exec', '-i', container, 'sh', '-ec',
        'export MYSQL_PWD="${MYSQL_ROOT_PASSWORD:?}"; exec mysql --user=root --batch --skip-column-names chengxinjian',
        ], input=statement, text=True, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, timeout=30)
    if result.returncode:
        raise RuntimeError('Database inspection failed')
    return result.stdout.strip()


def backup(args):
    stamp = dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    archive = args.directory / ('cxj-auto-'+stamp+'.sql.gz')
    result = subprocess.run(['python3', str(Path(__file__).with_name('mysql_backup.py')), 'backup',
        '--docker-container', args.container, '--database', 'chengxinjian', '--archive', str(archive)],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=900)
    if result.returncode:
        raise RuntimeError('Backup failed')
    digest = checksum(archive)
    metadata = dict(file=archive.name, sha256=digest, bytes=archive.stat().st_size, completedAt=int(time.time()),
                    database='chengxinjian', kind='full-logical', restored=False)
    atomic_json(archive.with_suffix(archive.suffix+'.json'), metadata)
    atomic_json(args.directory/'latest-auto.json', metadata)
    # Prune only this tool's files with matching manifests, after a successful new backup; always keep two.
    archives = sorted(args.directory.glob('cxj-auto-*.sql.gz'), reverse=True)
    cutoff = time.time()-args.retention_days*86400
    for old in archives[2:]:
        manifest = old.with_suffix(old.suffix+'.json')
        if old.is_symlink() or not manifest.is_file() or manifest.is_symlink() or old.stat().st_mtime >= cutoff:
            continue
        data = json.loads(manifest.read_text())
        if data.get('file') == old.name and data.get('sha256') == checksum(old):
            old.unlink()
            manifest.unlink()
    return dict(status='ok', operation='backup', archive=metadata)


def monitor(args):
    problems = []
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    try:
        with opener.open('http://127.0.0.1:18080/actuator/health/readiness', timeout=5) as r:
            if json.load(r).get('status') != 'UP':
                problems.append('readiness_down')
    except (OSError, ValueError):
        problems.append('readiness_unreachable')
    metrics = {}
    try:
        values = sql(args.container, """
SELECT (SELECT COUNT(*) FROM media_processing_job WHERE state='failed'),
 (SELECT COUNT(*) FROM lesson_trial WHERE state='failed'),
 (SELECT COUNT(*) FROM media_processing_job WHERE (state='running' AND lease_until<UTC_TIMESTAMP()-INTERVAL 5 MINUTE) OR (state='pending' AND next_attempt_at<UTC_TIMESTAMP()-INTERVAL 15 MINUTE)),
 (SELECT COUNT(*) FROM lesson_trial WHERE state IN ('running','pending') AND next_attempt_at<UTC_TIMESTAMP()-INTERVAL 15 MINUTE);
""").split('\t')
        metrics = dict(zip(['uploadFailed','trialFailed','uploadOverdue','trialOverdue'], map(int, values)))
        if len(metrics) != 4:
            raise ValueError()
        if any(metrics.values()):
            problems.append('media_jobs_need_attention')
    except (OSError, RuntimeError, ValueError, subprocess.TimeoutExpired):
        problems.append('task_inspection_failed')
    free = shutil.disk_usage(args.directory)
    metrics['diskFreeBytes'] = free.free
    if free.free < 5*1024**3 or free.free/free.total < .1:
        problems.append('disk_space_low')
    try:
        latest = json.loads((args.directory/'latest-auto.json').read_text())
        archive = args.directory/latest['file']
        if archive.parent != args.directory or archive.is_symlink() or not archive.is_file() or time.time()-latest['completedAt'] > 30*3600:
            problems.append('backup_missing_or_stale')
    except (OSError, ValueError, KeyError):
        problems.append('backup_missing_or_stale')
    return dict(status='attention' if problems else 'ok', operation='monitor', checkedAt=int(time.time()), problems=problems, metrics=metrics,
                externalNotificationConfigured=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['backup','monitor'])
    parser.add_argument('--directory', type=Path, default=Path('/var/backups/cxj'))
    parser.add_argument('--container', default='cxj_staging-mysql-1')
    parser.add_argument('--retention-days', type=int, default=14)
    args = parser.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]*', args.container) or not 2 <= args.retention_days <= 365:
        parser.error('Invalid container or retention')
    os.umask(0o077)
    args.directory = args.directory.absolute()
    args.directory.mkdir(mode=0o700, parents=True, exist_ok=True)
    if args.directory.is_symlink() or args.directory.stat().st_uid != os.getuid() or args.directory.stat().st_mode & 0o077:
        parser.error('Use an operator-owned private backup directory (0700)')
    with (args.directory/('.'+args.action+'.lock')).open('w') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        try:
            report = backup(args) if args.action == 'backup' else monitor(args)
        except (OSError, ValueError, RuntimeError, subprocess.TimeoutExpired):
            report = dict(status='failed', operation=args.action, checkedAt=int(time.time()))
        atomic_json(args.directory/('last-'+args.action+'.json'), report)
        print(json.dumps(report, ensure_ascii=False))
        return 0 if report['status'] == 'ok' else 1


if __name__ == '__main__':
    raise SystemExit(main())
