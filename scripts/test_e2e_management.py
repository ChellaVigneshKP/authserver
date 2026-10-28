"""Observation qualification checks; no SQL or HTTP connections."""
import importlib.util
from pathlib import Path
import unittest
import json
from unittest.mock import Mock, patch

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

portal_spec = importlib.util.spec_from_file_location('portal', Path(__file__).with_name('portal-dev.py'))
portal = importlib.util.module_from_spec(portal_spec)
portal_spec.loader.exec_module(portal)

class PortalReadinessTest(unittest.TestCase):
    row = dict(DatabaseName='AuthserverLifecycle_'+'a'*32, ClientId='owned-client')
    def test_oidc_ready_can_precede_owned_bootstrap_client(self):
        with patch.object(portal,'sql_text',side_effect=['',json.dumps([self.row])]), patch.object(portal.time,'sleep'):
            try:
                result=portal.wait_portal_client({},self.row['DatabaseName'],Mock(poll=Mock(return_value=None)))
            except json.JSONDecodeError:
                self.fail('OIDC readiness must wait through an empty SQL bootstrap result')
        self.assertEqual(result,self.row)
    def test_duplicate_or_foreign_database_client_is_refused(self):
        for rows in [[self.row,self.row],[self.row|dict(DatabaseName='AGSAuth')]]:
            with patch.object(portal,'sql_text',return_value=json.dumps(rows)):
                with self.assertRaisesRegex(RuntimeError,'fixture mismatch'):
                    portal.wait_portal_client({},self.row['DatabaseName'],Mock(poll=Mock(return_value=None)))
    def test_bootstrap_wait_has_a_fixed_deadline(self):
        with patch.object(portal,'sql_text',return_value=''), patch.object(portal.time,'monotonic',side_effect=[0,0,31]), patch.object(portal.time,'sleep'):
            try:
                with self.assertRaisesRegex(RuntimeError,'bootstrap readiness timeout'):
                    portal.wait_portal_client({},self.row['DatabaseName'],Mock(poll=Mock(return_value=None)))
            except json.JSONDecodeError:
                self.fail('Bootstrap timeout must be controlled without raw SQL response data')

if __name__ == '__main__': unittest.main()
