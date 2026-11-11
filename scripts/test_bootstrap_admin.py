import json
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch
import bootstrap_admin


class BootstrapWrapperTest(unittest.TestCase):
    def test_container_password_is_piped_not_exposed_in_command_or_environment(self):
        password='private-example-password-12345'
        with patch.object(bootstrap_admin.sys,'argv',['bootstrap_admin.py','--docker-container','cxj_staging-app-1']), \
             patch.object(bootstrap_admin.sys,'stdin',Mock(isatty=lambda:True)), \
             patch('builtins.input',side_effect=['owner','负责人','受控初始化']), \
             patch.object(bootstrap_admin.getpass,'getpass',side_effect=[password,password]), \
             patch.object(bootstrap_admin.subprocess,'run',return_value=SimpleNamespace(returncode=0)) as run:
            self.assertEqual(bootstrap_admin.main(),0)
        args,kwargs=run.call_args
        self.assertEqual(args[0][:4],['docker','exec','-i','cxj_staging-app-1'])
        self.assertNotIn(password,repr(args))
        self.assertNotIn(password,repr(kwargs['env']))
        self.assertEqual(json.loads(kwargs['input'])['password'],password)

    def test_untrusted_container_name_is_rejected_before_execution(self):
        with patch.object(bootstrap_admin.sys,'argv',['bootstrap_admin.py','--docker-container','bad;command']), \
             patch.object(bootstrap_admin.sys,'stdin',Mock(isatty=lambda:True)), \
             patch.object(bootstrap_admin.subprocess,'run') as run:
            with self.assertRaises(ValueError):
                bootstrap_admin.main()
            run.assert_not_called()


if __name__=='__main__':
    unittest.main()
