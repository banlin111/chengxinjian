#!/usr/bin/env python3
"""Create a private single-schema dump or verify it by restoring into a NEW schema."""
import argparse
import gzip
import hashlib
import os
from pathlib import Path
import re
import shutil
import stat
import subprocess
import tempfile


def run():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['backup', 'restore-drill'])
    access = parser.add_mutually_exclusive_group(required=True)
    access.add_argument('--client-config', type=Path)
    access.add_argument('--docker-container', help='MySQL service container from the isolated Compose stack')
    parser.add_argument('--bin-dir', type=Path)
    parser.add_argument('--database', required=True)
    parser.add_argument('--archive', required=True, type=Path)
    parser.add_argument('--sha256', help='Expected digest printed by the backup operation')
    args = parser.parse_args()
    schema = args.database
    if not re.fullmatch(r'[A-Za-z][A-Za-z0-9_]{0,60}', schema):
        parser.error('Invalid schema name')
    if args.client_config:
        info = args.client_config.lstat()
        if not stat.S_ISREG(info.st_mode) or info.st_uid != os.getuid() or info.st_mode & 0o077:
            parser.error('Client configuration must be a private file owned by the operator')
    elif args.bin_dir or not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]*', args.docker_container):
        parser.error('Invalid container target')

    def command(name, *options):
        if args.docker_container:
            return ['docker', 'exec', '-i', args.docker_container, 'sh', '-ec',
                    'export MYSQL_PWD="${MYSQL_ROOT_PASSWORD:?}"; exec "$@"', 'cxj-mysql',
                    name, '--user=root', *options]
        executable = str(args.bin_dir / name) if args.bin_dir else name
        return [executable, '--defaults-file=' + str(args.client_config.resolve()), *options]

    def digest(file):
        hasher = hashlib.sha256()
        for chunk in iter(lambda: file.read(1024 * 1024), b''):
            hasher.update(chunk)
        return hasher.hexdigest()

    if args.action == 'backup':
        # Link after closing and validating the temporary archive. Never replace a previous backup.
        with tempfile.NamedTemporaryFile(dir=args.archive.parent, prefix='.cxj-dump-') as temp:
            with gzip.GzipFile(fileobj=temp, mode='wb') as compressed:
                cmd = command('mysqldump', '--single-transaction', '--quick', '--no-tablespaces', '--hex-blob',
                              '--set-gtid-purged=OFF', '--skip-add-drop-table', '--skip-lock-tables', schema)
                with subprocess.Popen(cmd, stdout=subprocess.PIPE) as proc:
                    shutil.copyfileobj(proc.stdout, compressed)
                    if proc.wait():
                        raise RuntimeError('Dump failed')
            temp.flush()
            os.fsync(temp.fileno())
            temp.seek(0)
            checksum = digest(temp)
            os.link(temp.name, args.archive)
            print('SHA256', checksum)
        return

    if not schema.startswith('cxj_restore_') or not re.fullmatch('[a-fA-F0-9]{64}', args.sha256 or ''):
        parser.error('Use a fresh cxj_restore_* schema and the recorded SHA-256')
    os.umask(0o077)
    with args.archive.open('rb') as source:
        if digest(source) != args.sha256.lower():
            raise RuntimeError('Backup digest mismatch')
        source.seek(0)
        # CREATE without IF NOT EXISTS refuses an occupied target even on a retry.
        subprocess.run(command('mysql', '-e', 'CREATE DATABASE '+schema+' CHARACTER SET utf8mb4'), check=True)
        with gzip.GzipFile(fileobj=source) as uncompressed, subprocess.Popen(command('mysql', schema), stdin=subprocess.PIPE) as proc:
            shutil.copyfileobj(uncompressed, proc.stdin)
            proc.stdin.close()
            if proc.wait():
                raise RuntimeError('Restore failed in the new drill schema')
    subprocess.run(command('mysql', '--batch', schema, '-e',
        'SELECT version, success FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank'), check=True)


if __name__ == '__main__':
    run()
