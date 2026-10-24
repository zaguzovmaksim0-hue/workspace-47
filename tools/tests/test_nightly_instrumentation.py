"""Regression contract for nightly synthetic emulator verification."""
import unittest
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]


class NightlyInstrumentationTest(unittest.TestCase):
    def setUp(self):
        self.source = (ROOT / '.github/workflows/ci.yml').read_text()
        self.workflow = yaml.load(self.source, Loader=yaml.BaseLoader)

    def test_nightly_schedule_uses_off_peak_utc(self):
        self.assertEqual([{'cron': '23 2 * * *'}], self.workflow['on']['schedule'])
        manual = self.workflow['on']['workflow_dispatch']['inputs']['run_instrumentation']
        self.assertEqual('false', manual['default'])
        self.assertEqual('boolean', manual['type'])

    def test_only_schedule_or_explicit_manual_input_enables_both_emulator_jobs(self):
        expected = "${{ github.event_name == 'schedule' || (github.event_name == 'workflow_dispatch' && inputs.run_instrumentation) }}"
        for job_id in ('android-instrumented', 'android-release-r8-smoke'):
            job = self.workflow['jobs'][job_id]
            self.assertEqual(expected, job['if'])
            self.assertNotIn('continue-on-error', job)
            self.assertNotIn('environment', job)
            self.assertNotIn('secrets.', str(job))

    def test_nightly_and_manual_runs_do_not_cancel_main_push_verification(self):
        self.assertEqual('ci-${{ github.workflow }}-${{ github.event_name }}-${{ github.ref }}', self.workflow['concurrency']['group'])
        self.assertEqual('true', self.workflow['concurrency']['cancel-in-progress'])

    def test_existing_synthetic_harness_and_exact_commit_remain_in_use(self):
        job = self.workflow['jobs']['android-instrumented']
        scripts = [step.get('with', {}).get('script') for step in job['steps']]
        self.assertIn('bash scripts/run-android-instrumentation-ci.sh', scripts)
        self.assertEqual({'contents': 'read'}, self.workflow['permissions'])
        for job_id in ('android-instrumented', 'android-release-r8-smoke'):
            checkout = self.workflow['jobs'][job_id]['steps'][0]
            self.assertEqual('${{ env.VERIFY_REF }}', checkout['with']['ref'])
            self.assertEqual('false', checkout['with']['persist-credentials'])


if __name__ == '__main__':
    unittest.main()
