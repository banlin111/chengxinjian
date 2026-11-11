import json
from pathlib import Path
import tempfile
import time
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import staging_maintenance as maintenance


class MaintenanceTest(unittest.TestCase):
    def test_successful_backup_only_prunes_verified_old_automatic_archives(self):
        with tempfile.TemporaryDirectory() as d:
            directory = Path(d)
            old = directory/'cxj-auto-20000101T000000000000Z.sql.gz'
            old.write_bytes(b'old')
            maintenance.atomic_json(old.with_suffix('.gz.json'), {'file':old.name,'sha256':maintenance.checksum(old)})
            import os
            os.utime(old, (1, 1))
            manual = directory/'manual.sql.gz'
            manual.write_bytes(b'keep')
            recent = directory/'cxj-auto-20261110T000000000000Z.sql.gz'
            recent.write_bytes(b'recent')
            maintenance.atomic_json(recent.with_suffix('.gz.json'), {'file':recent.name,'sha256':maintenance.checksum(recent)})
            def dump(command, **kwargs):
                Path(command[-1]).write_bytes(b'new')
                return SimpleNamespace(returncode=0)
            with patch.object(maintenance.subprocess,'run',side_effect=dump):
                report = maintenance.backup(SimpleNamespace(directory=directory,container='fixture',retention_days=14))
            self.assertEqual(report['status'],'ok')
            self.assertFalse(old.exists())
            self.assertTrue(manual.exists())
            self.assertTrue(recent.exists())
            latest=json.loads((directory/'latest-auto.json').read_text())
            self.assertEqual(maintenance.checksum(directory/latest['file']),latest['sha256'])

    def test_failed_backup_does_not_prune_or_replace_latest(self):
        with tempfile.TemporaryDirectory() as d:
            directory=Path(d)
            latest=directory/'latest-auto.json'
            latest.write_text('{"preserved":true}')
            with patch.object(maintenance.subprocess,'run',return_value=SimpleNamespace(returncode=1)):
                with self.assertRaises(RuntimeError):
                    maintenance.backup(SimpleNamespace(directory=directory,container='fixture',retention_days=14))
            self.assertEqual(json.loads(latest.read_text()),{'preserved':True})

    def test_failed_tasks_and_missing_backup_raise_monitor_attention(self):
        with tempfile.TemporaryDirectory() as d:
            with patch.object(maintenance,'sql',return_value='1\t0\t0\t2'):
                report=maintenance.monitor(SimpleNamespace(directory=Path(d),container='fixture'))
            self.assertEqual(report['status'],'attention')
            self.assertIn('media_jobs_need_attention',report['problems'])
            self.assertIn('backup_missing_or_stale',report['problems'])
            self.assertFalse(report['externalNotificationConfigured'])


if __name__ == '__main__':
    unittest.main()
