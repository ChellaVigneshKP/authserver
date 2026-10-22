"""Deterministic cleanup ownership checks; never connects to Redis."""
import importlib.util
from pathlib import Path
import subprocess
import unittest

spec = importlib.util.spec_from_file_location('lifecycles', Path(__file__).with_name('e2e-isolated-lifecycles.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class CleanupTest(unittest.TestCase):
    prefix = 'authserver:lifecycle:' + 'a'*32 + ':'

    def test_cleans_and_verifies_both_configured_databases_only_owned_keys(self):
        calls = []
        keys = {0: [self.prefix+'session:one'], 1: [self.prefix+'cache:two']}
        def run(command, **kwargs):
            calls.append(command)
            db = int(command[command.index('-n')+1])
            if '--scan' in command:
                return subprocess.CompletedProcess(command, 0, '\n'.join(keys[db])+'\n' if keys[db] else '')
            self.assertEqual(command[command.index('DEL')+1:], keys[db])
            keys[db] = []
            return subprocess.CompletedProcess(command, 0, '1\n')
        self.assertEqual(module.cleanup_redis_owned({}, self.prefix, run), [
            {'database':0, 'removed':1, 'remaining':0}, {'database':1, 'removed':1, 'remaining':0}])
        self.assertEqual(len(calls), 6)
        self.assertTrue(all('FLUSHDB' not in command and 'FLUSHALL' not in command for command in calls))

    def test_configured_databases_and_deduplication(self):
        self.assertEqual(module.configured_redis_databases({'SPRING_DATA_REDIS_DATABASE':'2',
            'SPRING_APPLICATION_JSON':'{"spring.data.redis.cache.database":3}'}), [2, 3])
        self.assertEqual(module.configured_redis_databases({'SPRING_DATA_REDIS_CACHE_DATABASE':'0'}), [0])
        self.assertEqual(module.configured_redis_databases({'SPRING_APPLICATION_JSON':
            '{"spring":{"data":{"redis":{"database":2,"cache":{"database":3}}}}}'}), [2, 3])

    def test_refuses_unsafe_database_or_prefix_before_command(self):
        for config, prefix in [({}, 'authserver:*'), ({'SPRING_DATA_REDIS_DATABASE':'-1'}, self.prefix),
                ({'SPRING_DATA_REDIS_CACHE_DATABASE':'1;FLUSHDB'}, self.prefix),
                ({'SPRING_DATA_REDIS_DATABASE':'16'}, self.prefix)]:
            with self.assertRaises(ValueError):
                module.cleanup_redis_owned(config, prefix, lambda *a, **k: self.fail('unsafe command'))

    def test_refuses_scan_result_outside_owned_prefix(self):
        def run(command, **kwargs):
            self.assertNotIn('DEL', command)
            return subprocess.CompletedProcess(command, 0, 'shared:key\n')
        with self.assertRaises(ValueError): module.cleanup_redis_owned({}, self.prefix, run)

    def test_nonzero_remaining_fails_cleanup(self):
        def run(command, **kwargs):
            return subprocess.CompletedProcess(command, 0, self.prefix+'session:one\n' if '--scan' in command else '1\n')
        with self.assertRaises(RuntimeError): module.cleanup_redis_owned({}, self.prefix, run)

if __name__ == '__main__': unittest.main()
