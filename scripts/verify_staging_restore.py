#!/usr/bin/env python3
"""Restore the latest automatic backup into a new isolated schema and compare table checksums.
Run while staging writes are quiescent. A mismatch never overwrites the source database.
"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import uuid
from staging_maintenance import atomic_json, sql


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory',type=Path,default=Path('/var/backups/cxj'))
    parser.add_argument('--container',default='cxj_staging-mysql-1')
    args=parser.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]*',args.container):raise ValueError('Invalid container')
    os.umask(0o077)
    latest=json.loads((args.directory/'latest-auto.json').read_text())
    if not re.fullmatch(r'cxj-auto-[0-9]{8}T[0-9]{12}Z\.sql\.gz',latest['file']):raise ValueError('Invalid archive')
    schema='cxj_restore_'+uuid.uuid4().hex
    def tables(database):
        result=sql(args.container,'SHOW TABLES FROM '+database).splitlines()
        if any(not re.fullmatch(r'[a-zA-Z0-9_]+',t) for t in result):raise ValueError('Invalid table name')
        return result
    source_tables=tables('chengxinjian')
    def checks(database, names):
        result={}
        for t in names:
            row=sql(args.container,'CHECKSUM TABLE '+database+'.'+t).split('\t')
            if len(row)!=2 or row[1]=='NULL':raise ValueError('Checksum unavailable')
            result[t]=row[1]
        return result
    before=checks('chengxinjian',source_tables)
    subprocess.run(['python3',str(Path(__file__).with_name('mysql_backup.py')),'restore-drill','--docker-container',args.container,
                    '--database',schema,'--archive',str(args.directory/latest['file']),'--sha256',latest['sha256']],
                   check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=900)
    try:
        restored=tables(schema)
        after=checks('chengxinjian',source_tables)
        actual=checks(schema,restored)
        if before!=after or source_tables!=restored or actual!=before:
            raise ValueError('Source changed or restore checksum mismatch; source has not been modified')
        migrations=sql(args.container,'SELECT version FROM '+schema+'.flyway_schema_history WHERE success=1 AND version IS NOT NULL ORDER BY installed_rank').splitlines()
        report=dict(status='ok',archive=latest['file'],sha256=latest['sha256'],tables=len(restored),migrations=migrations,
                    sourceUnchanged=True,scope='quiescent isolated staging database; not COS or continuous recovery')
    finally:
        # Only drop this invocation's freshly created isolated drill schema.
        sql(args.container,'DROP DATABASE '+schema)
    report['drillSchemaRemoved']=True
    atomic_json(args.directory/'last-restore.json',report)
    print(json.dumps(report))


if __name__=='__main__':
    try:main()
    except (OSError,ValueError,KeyError,subprocess.SubprocessError):
        print('Restore drill failed. Source database was not overwritten; inspect private backup and database state.')
        raise SystemExit(1)
