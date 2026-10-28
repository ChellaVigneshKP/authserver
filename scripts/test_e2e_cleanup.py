"""Deterministic cleanup ownership checks; never connects to Redis."""
import importlib.util
import json
import tempfile
from unittest.mock import patch
from pathlib import Path
import subprocess
import unittest

spec = importlib.util.spec_from_file_location('lifecycles', Path(__file__).with_name('e2e-isolated-lifecycles.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class CleanupTest(unittest.TestCase):
    prefix = 'authserver:lifecycle:' + 'a'*32 + ':'

    def target(self, databases=(0, 1)):
        return dict(host='127.0.0.1', port=16379, container_port=6379, container_id='d'*64,
                    databases=list(databases), published_endpoint_verified=True)

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
        self.assertEqual(module.cleanup_redis_owned(self.target(), self.prefix, run), [
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
        for target, prefix in [(self.target(), 'authserver:*'), (self.target([-1]), self.prefix),
                               (self.target(['1;FLUSHDB']), self.prefix), (self.target([16]), self.prefix),
                               (self.target() | {'container_id':'unsafe;container'}, self.prefix)]:
            with self.assertRaises(ValueError):
                module.cleanup_redis_owned(target, prefix, lambda *a, **k: self.fail('unsafe command'))

    def test_refuses_scan_result_outside_owned_prefix(self):
        def run(command, **kwargs):
            self.assertNotIn('DEL', command)
            return subprocess.CompletedProcess(command, 0, 'shared:key\n')
        with self.assertRaises(ValueError): module.cleanup_redis_owned(self.target(), self.prefix, run)

    def test_nonzero_remaining_fails_cleanup(self):
        def run(command, **kwargs):
            return subprocess.CompletedProcess(command, 0, self.prefix+'session:one\n' if '--scan' in command else '1\n')
        with self.assertRaises(RuntimeError): module.cleanup_redis_owned(self.target(), self.prefix, run)

    def base_config(self):
        return {'REDIS_HOST':'127.0.0.1', 'REDIS_PORT':'16379'}

    def test_one_effective_child_environment_includes_inherited_database_overrides(self):
        inherited = {'SPRING_DATA_REDIS_DATABASE':'2', 'SPRING_DATA_REDIS_CACHE_DATABASE':'3'}
        child = module.effective_child_environment(self.base_config(), inherited, self.prefix)
        self.assertEqual(module.configured_redis_databases(child), [2, 3])
        properties = json.loads(child['SPRING_APPLICATION_JSON'])
        self.assertEqual(properties['spring.data.redis.database'], 2)
        self.assertEqual(properties['spring.data.redis.cache.database'], 3)
        self.assertEqual(properties['spring.session.redis.namespace'], self.prefix+'session')
        self.assertEqual(properties['spring.data.redis.host'], '127.0.0.1')
        self.assertEqual(properties['spring.data.redis.port'], 16379)

    def test_rejects_effective_unsafe_endpoint_and_unsupported_transport(self):
        for override in [dict(SPRING_DATA_REDIS_HOST='remote.example'),
                         dict(SPRING_DATA_REDIS_PORT='16380'),
                         dict(SPRING_DATA_REDIS_SENTINEL_ENABLED='true'),
                         dict(SPRING_DATA_REDIS_SSL_ENABLED='true'),
                         dict(SPRING_DATA_REDIS_URL='redis://127.0.0.1:16380'),
                         dict(SPRING_DATA_REDIS_PASSWORD='synthetic-unsupported-auth'),
                         dict(JAVA_TOOL_OPTIONS='-Dspring.data.redis.port=16380'),
                         dict(SPRING_CONFIG_LOCATION='/unverified/properties'),
                         dict(SPRING_APPLICATION_JSON='{"spring":{"data":{"redis":{"port":16380}}}}')]:
            with self.subTest(keys=list(override)), self.assertRaises(ValueError):
                module.effective_child_environment(self.base_config(), override, self.prefix)

    def test_docker_endpoint_verification_requires_exact_published_host_port(self):
        child = module.effective_child_environment(self.base_config(), {}, self.prefix)
        container = dict(id='d'*64, running=True, ports={'6379/tcp':[dict(HostIp='127.0.0.1', HostPort='16379')]})
        def run(command, **kwargs):
            self.assertEqual(command[:2], ['docker', 'inspect'])
            self.assertNotIn('DEL', command)
            return subprocess.CompletedProcess(command, 0, json.dumps(container))
        target = module.verified_redis_target(child, run)
        self.assertEqual(target['container_id'], 'd'*64)
        self.assertEqual(target['databases'], [0, 1])
        for binding in [dict(HostIp='127.0.0.1', HostPort='16380'),
                        dict(HostIp='0.0.0.0', HostPort='16379'),
                        dict(HostIp='::1', HostPort='16379')]:
            container['ports']['6379/tcp'] = [binding]
            with self.assertRaises(ValueError): module.verified_redis_target(child, run)

    def test_inherited_nondefault_databases_are_cleaned_via_verified_container(self):
        child = module.effective_child_environment(self.base_config(),
            {'SPRING_DATA_REDIS_DATABASE':'2', 'SPRING_DATA_REDIS_CACHE_DATABASE':'3'}, self.prefix)
        container = dict(id='d'*64, running=True, ports={'6379/tcp':[dict(HostIp='127.0.0.1', HostPort='16379')]})
        target = module.verified_redis_target(child, lambda command, **kwargs: subprocess.CompletedProcess(command, 0, json.dumps(container)))
        keys = {2:[self.prefix+'session:owned'], 3:[self.prefix+'cache:owned']}
        calls = []
        def run(command, **kwargs):
            calls.append(command)
            self.assertEqual(command[:8], ['docker', 'exec', target['container_id'], 'redis-cli', '-h', '127.0.0.1', '-p', '6379'])
            db = int(command[command.index('-n')+1])
            self.assertIn(db, (2, 3))
            if '--scan' in command:
                return subprocess.CompletedProcess(command, 0, '\n'.join(keys[db]))
            self.assertEqual(command[command.index('DEL')+1:], keys[db])
            keys[db] = []
            return subprocess.CompletedProcess(command, 0, '1\n')
        self.assertEqual(module.cleanup_redis_owned(target, self.prefix, run), [
            dict(database=2, removed=1, remaining=0), dict(database=3, removed=1, remaining=0)])
        self.assertEqual(len(calls), 6)

    def test_invalid_inherited_endpoint_rejected_before_database_or_jvm_side_effects(self):
        config = self.base_config() | {'DB_URL':'jdbc:sqlserver://127.0.0.1:15433;databaseName=AGSAuth;encrypt=true;trustServerCertificate=true'}
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root/'target').mkdir()
            (root/'target/authserver-0.0.1-SNAPSHOT.jar').touch()
            with patch.object(module, 'ROOT', root), patch.object(module, 'load_target', return_value=(config, '', 'AGSAuth')), \
                    patch.dict(module.os.environ, {'SPRING_DATA_REDIS_PORT':'16380'}), \
                    patch.object(module, 'source_provenance', return_value={}), \
                    patch.object(module, 'sql_text', side_effect=AssertionError('unexpected database effect')) as database, \
                    patch.object(module.subprocess, 'Popen', side_effect=AssertionError('unexpected JVM launch')), \
                    patch.object(module.signal, 'signal'), patch.object(module.socket, 'socket') as probe:
                probe.return_value.__enter__.return_value.getsockname.return_value = ('127.0.0.1', 19081)
                with self.assertRaises(ValueError): module.main()
                database.assert_not_called()
                self.assertFalse((root/'.local').exists())

if __name__ == '__main__': unittest.main()
