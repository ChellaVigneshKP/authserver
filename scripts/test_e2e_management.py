"""Observation qualification checks; no SQL or HTTP connections."""
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('management', Path(__file__).with_name('e2e-admin-management.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class ObservationsTest(unittest.TestCase):
    provenance = dict(source_commit='a'*40, source_tree_sha256='b'*64)
    def event(self, status=200):
        return dict(scenario='test#actualRequest', method='PUT', url='/services/api/v1/users/{id}/metadata',
                    actor_class='human_tenant', status=status, sql_outcome=dict(readback='completed',changed_tables=[]), **self.provenance)
    def test_denial_is_observation_without_positive_qualification(self):
        check = module.qualify_observations([self.event(403)], True, self.provenance)[0]
        self.assertTrue(check['passed'])
        self.assertFalse(check['positive'])
    def test_failed_suite_cannot_import_positive_success(self):
        check = module.qualify_observations([self.event()], False, self.provenance)[0]
        self.assertFalse(check['passed'])
        self.assertFalse(check['positive'])
    def test_rejects_junit_names_in_place_of_real_observations_and_stale_provenance(self):
        for event in [dict(name='routePassed'), self.event() | dict(source_commit='c'*40), self.event() | dict(sql_outcome={})]:
            with self.assertRaises(ValueError): module.qualify_observations([event], True, self.provenance)
    def test_matrix_requires_denial_and_unchanged_sql_without_inventing_positive(self):
        event = self.event(401) | dict(scenario='test#everyManagementMutationRejectsMissingBearerWithoutSqlChanges')
        matrix = module.mutation_matrix(module.qualify_observations([event], True, self.provenance))
        self.assertEqual(len(matrix), 1)
        self.assertTrue(matrix[0]['denial_with_unchanged_sql'])
        self.assertFalse(matrix[0]['positive_http_observed'])

if __name__ == '__main__': unittest.main()
