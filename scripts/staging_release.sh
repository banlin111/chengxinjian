#!/bin/sh
# Start an already-built staging image. TLS terminates at the host reverse proxy.
set -eu
if [ "$#" -ne 2 ]; then
  echo 'Usage: staging_release.sh <private-compose-env> <existing-image-tag-or-digest>' >&2
  exit 2
fi
env_file=$1
CXJ_IMAGE=$2
export CXJ_IMAGE
repo=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
python3 - "$env_file" <<'PY'
import os, stat, sys
info = os.lstat(sys.argv[1])
if not stat.S_ISREG(info.st_mode) or info.st_uid != os.getuid() or info.st_mode & 0o077:
    sys.exit('Use a private Compose environment file owned by the deployment operator.')
PY
docker image inspect "$CXJ_IMAGE" >/dev/null
docker compose --env-file "$env_file" -f "$repo/infra/compose.yml" -f "$repo/infra/compose.staging.yml" up -d --pull missing app
python3 - <<'PY'
import json, time, urllib.error, urllib.request
url = 'http://127.0.0.1:18080'
for attempt in range(90):
    try:
        with urllib.request.urlopen(url+'/actuator/health/readiness', timeout=2) as response:
            if json.load(response).get('status') == 'UP':
                break
    except (OSError, ValueError):
        pass
    time.sleep(2)
else:
    raise SystemExit('Readiness timeout. Inspect service state and Flyway history before switching traffic.')
with urllib.request.urlopen(url+'/api/v1/courses?pageSize=1', timeout=5) as response:
    if json.load(response).get('code') != 'OK':
        raise SystemExit('Course catalog check failed.')
print('Readiness UP; course catalog OK.')
PY
