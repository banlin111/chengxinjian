"""Security checks for the explicit local environment loader; uses disposable fake values only."""
import os
from pathlib import Path
import tempfile
import unittest
import subprocess
import sys
from local_backend import load_private

class PrivateConfigTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.path = Path(self.directory.name)/'private.env'
    def write(self, content, mode=0o600):
        self.path.write_text(content)
        self.path.chmod(mode)
    def test_values_are_literal_and_allowlisted(self):
        self.write('WX_APP_SECRET=fake&a=$(touch unexpected)\nOTHER=excluded\n')
        self.assertEqual(load_private(self.path,{'WX_APP_SECRET'}),{'WX_APP_SECRET':'fake&a=$(touch unexpected)'})
        self.assertFalse((Path(self.directory.name)/'unexpected').exists())
    def test_readable_by_other_users_is_rejected(self):
        self.write('KEY=fake',0o644)
        with self.assertRaises(ValueError): load_private(self.path,{'KEY'})
    def test_symlinks_are_rejected(self):
        self.write('KEY=fake')
        alias=self.path.with_name('alias.env')
        alias.symlink_to(self.path)
        with self.assertRaises(OSError): load_private(alias,{'KEY'})
    def test_duplicate_keys_are_rejected(self):
        self.write('KEY=first\nKEY=second\n')
        with self.assertRaises(ValueError): load_private(self.path,{'KEY'})
    def test_test_action_cannot_load_live_cloud_credentials(self):
        result=subprocess.run([sys.executable,str(Path(__file__).with_name('local_backend.py')),'test','--cloud'],capture_output=True,text=True)
        self.assertEqual(result.returncode,2)
        self.assertIn('Local configuration could not be loaded',result.stderr)

if __name__=='__main__': unittest.main()
