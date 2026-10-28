"""Pure validation checks: no SQL, HTTP or credentials are loaded."""
import importlib.util
from pathlib import Path
import unittest

spec=importlib.util.spec_from_file_location('e2e_target',Path(__file__).with_name('e2e_target.py'))
module=importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class TargetTest(unittest.TestCase):
    def config(self, database='AuthserverLifecycle_'+'a'*32):
        return dict(DB_URL='jdbc:sqlserver://127.0.0.1:15433;databaseName='+database+';encrypt=true;trustServerCertificate=true',SERVER_BASE_PATH='http://localhost:19081/services')
    def test_owned_database_and_http_must_match_private_config(self):
        config=self.config()
        self.assertEqual(module.validate_target(config,dict(AUTH_E2E_DB='AuthserverLifecycle_'+'a'*32,AUTH_E2E_BASE=config['SERVER_BASE_PATH'])),(config['SERVER_BASE_PATH'],'AuthserverLifecycle_'+'a'*32))
    def test_reject_shared_sql_override_for_owned_http(self):
        with self.assertRaises(ValueError): module.validate_target(self.config(),dict(AUTH_E2E_DB='AGSAuth'))
    def test_reject_http_override_not_matching_owned_config(self):
        with self.assertRaises(ValueError): module.validate_target(self.config(),dict(AUTH_SMOKE_BASE='http://localhost:9080/services'))
    def test_reject_remote_database_and_unsafe_database_names(self):
        for config in [self.config('master'),self.config('AGSAuth;DROP'),dict(DB_URL=self.config()['DB_URL'].replace('127.0.0.1','remote.example'))]:
            with self.assertRaises(ValueError): module.validate_target(config,{})
    def test_default_database_compatibility(self):
        self.assertEqual(module.validate_target(self.config('AGSAuth'),{})[1],'AGSAuth')

if __name__=='__main__': unittest.main()
