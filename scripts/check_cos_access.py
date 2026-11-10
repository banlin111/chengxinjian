#!/usr/bin/env python3
"""Read-only COS connectivity check; private credentials are never printed."""
import json
import logging
import re
import sys

from local_backend import ROOT, load_private


def main():
    logging.disable(logging.CRITICAL)
    from qcloud_cos import CosConfig, CosS3Client
    from qcloud_cos.cos_exception import CosServiceError

    config = load_private(ROOT / '.env.cloud.local', {
        'CLOUD_PROVIDER', 'STORAGE_BUCKET', 'STORAGE_REGION',
        'CLOUD_SECRET_ID', 'CLOUD_SECRET_KEY',
    })
    if config.get('CLOUD_PROVIDER') != 'tencent' or not all(config.get(k) for k in (
        'STORAGE_BUCKET', 'STORAGE_REGION', 'CLOUD_SECRET_ID', 'CLOUD_SECRET_KEY',
    )):
        raise ValueError('Incomplete config')
    if not re.fullmatch(r'[a-z0-9-]+-\d+', config['STORAGE_BUCKET']) or not re.fullmatch(
        r'[a-z]+-[a-z]+(?:-\d+)?', config['STORAGE_REGION'],
    ):
        raise ValueError('Invalid destination')
    client = CosS3Client(CosConfig(
        Region=config['STORAGE_REGION'], SecretId=config['CLOUD_SECRET_ID'],
        SecretKey=config['CLOUD_SECRET_KEY'], Scheme='https', Timeout=10,
    ), retry=0)
    results = {}
    for name, operation in (
        ('head_bucket', client.head_bucket), ('get_bucket_cors', client.get_bucket_cors),
    ):
        try:
            response = operation(Bucket=config['STORAGE_BUCKET'])
            results[name] = {'ok': True, 'http_status': 200}
            if name == 'get_bucket_cors':
                results[name]['rule_count'] = len(response.get('CORSRule', []))
        except CosServiceError as error:
            code = error.get_error_code()
            # Never print exception messages, headers, signed URLs or raw responses.
            results[name] = {
                'ok': False, 'http_status': int(error.get_status_code()),
                'code': code if re.fullmatch(r'[A-Za-z0-9_.-]{1,80}', code or '') else 'ProviderError',
            }
    print(json.dumps(results, ensure_ascii=False))
    return 0 if results['head_bucket']['ok'] else 1


if __name__ == '__main__':
    try:
        sys.exit(main())
    except Exception:
        print('COS check failed; verify private config, SDK installation and network. No credentials were printed.', file=sys.stderr)
        sys.exit(2)
