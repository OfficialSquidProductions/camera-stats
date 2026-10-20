"""Public/synthetic fixtures only; these tests never create signing keys or trust stores.

Synthetic probe dictionaries test contract validation, not native Authenticode.
The cryptographic test uses RFC8032 section7.1 test3 verbatim public material.
"""
import base64
import copy
from datetime import datetime, timedelta, timezone
import hashlib
import os
from pathlib import Path
import shutil
import tempfile
import unittest

import stage_windows_update as stage


class StagingTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix='shot-reporter-public-stage-tests-')
        self.addCleanup(self.tmp.cleanup)
        self.home = Path(self.tmp.name)
        self.msi = self.home/'Shot-Reporter-Windows-0.2.0-alpha.4.msi'
        self.msi.write_bytes(b'SYNTHETIC CONTRACT FIXTURE - NOT AN MSI OR SIGNED RELEASE')
        self.cert = b'PUBLIC SYNTHETIC CERTIFICATE CONTRACT FIXTURE - NOT A REAL CERTIFICATE'
        self.pin = hashlib.sha256(self.cert).hexdigest()
        self.hash = stage.digest(self.msi)
        self.checksums = self.home/'SHA256SUMS.txt'
        self.checksums.write_text(f'{self.hash}  {self.msi.name}\n')
        self.current = {'version':'0.2.0-alpha.3','msiVersion':'0.2.2','build':5,'sourceCommit':'a'*40,'platform':'windows-x64'}
        self.target = {'version':'0.2.0-alpha.4','msiVersion':'0.2.3','build':6,'sourceCommit':'b'*40,'platform':'windows-x64','distribution':'trusted signed manual MSI','files':{self.msi.name:self.hash},'windowsSigning':{'authenticodeStatus':'Valid','timestamped':True,'signerSHA256':self.pin,'publisher':'SYNTHETIC CONTRACT FIXTURE'}}
        public = bytes.fromhex('fc51cd8e6218a1a38da47ed00230f0580816ed13ba3303ac5deb911548908025')
        self.public = stage.SPKI_PREFIX + public
        self.policy = {'feedURL':stage.FEED_URL,'publicKey':base64.b64encode(self.public).decode(),'allowedHosts':sorted(stage.HOSTS),'signerSHA256':self.pin,'currentVersion':self.current['version'],'currentBuild':5,'currentMSIVersion':'0.2.2','defaultChannel':'alpha'}
        self.probe = {'status':'Valid','timestamped':True,'sha256':self.hash,'size':self.msi.stat().st_size,'certificate':base64.b64encode(self.cert).decode(),'subject':'SYNTHETIC CONTRACT FIXTURE','ekus':['1.3.6.1.5.5.7.3.3'],'productName':'Shot Reporter','productVersion':'0.2.3','upgradeCode':'{'+stage.UPGRADE_CODE.upper()+'}','template':'x64;1033'}

    def candidate(self, target=None, probe=None):
        return stage.candidate_check(self.msi,target or self.target,self.checksums,self.cert,'b'*40,probe or self.probe)

    def test_contract_accepts_only_matching_independent_metadata(self):
        # This is a pure contract test, not production signature acceptance.
        self.assertEqual(self.candidate(), self.hash)
        self.assertEqual(stage.policy_check(self.policy,self.current,self.cert), self.public)

    def test_bytes_or_native_hash_mismatch_is_refused(self):
        self.msi.write_bytes(self.msi.read_bytes()+b'changed')
        with self.assertRaisesRegex(ValueError,'bytes differ'):
            self.candidate()
        self.target['files'][self.msi.name]=stage.digest(self.msi)
        self.checksums.write_text(f'{stage.digest(self.msi)}  {self.msi.name}\n')
        with self.assertRaisesRegex(ValueError,'exact MSI bytes'):
            self.candidate()

    def test_unsigned_or_claim_only_signing_is_refused(self):
        for field, value in [('distribution','unsigned manual MSI'),('windowsSigning',{})]:
            target=copy.deepcopy(self.target);target[field]=value
            with self.subTest(field=field), self.assertRaises(ValueError):
                self.candidate(target=target)
        probe=copy.deepcopy(self.probe);probe['status']='NotSigned'
        with self.assertRaisesRegex(ValueError,'trust/timestamp'):
            self.candidate(probe=probe)

    def test_publisher_msi_identity_and_timestamp_claims_are_bound(self):
        wrong = {'certificate':base64.b64encode(b'x'*len(self.cert)).decode(),'subject':'OTHER','productName':'OTHER','productVersion':'0.2.2','upgradeCode':'{00000000-0000-0000-0000-000000000000}','template':'Intel;1033','timestamped':False,'ekus':[]}
        for field, value in wrong.items():
            probe=copy.deepcopy(self.probe);probe[field]=value
            with self.subTest(field=field), self.assertRaises(ValueError):
                self.candidate(probe=probe)

    def test_azure_rotating_profile_cannot_use_fixed_leaf_policy(self):
        probe=copy.deepcopy(self.probe);probe['ekus']+=['1.3.6.1.4.1.311.97.1.2.3.4']
        with self.assertRaisesRegex(ValueError,'durable-profile'):
            self.candidate(probe=probe)

    def test_policy_rejects_cross_app_hosts_stale_bootstrap_or_wrong_pin(self):
        for field,value in [('feedURL','https://evil.test/feed.json'),('allowedHosts',sorted(stage.HOSTS|{'evil.test'})),('currentBuild',4),('currentMSIVersion','0.2.1'),('signerSHA256','0'*64),('publicKey',base64.b64encode(b'x'*44).decode())]:
            policy=copy.deepcopy(self.policy);policy[field]=value
            with self.subTest(field=field), self.assertRaises(ValueError):
                stage.policy_check(policy,self.current,self.cert)

    def test_duplicate_json_and_checksum_entries_fail(self):
        path=self.home/'duplicate.json';path.write_text('{"build":5,"build":6}')
        with self.assertRaisesRegex(ValueError,'Duplicate'):
            stage.read_json(path)
        self.checksums.write_text((f'{self.hash}  {self.msi.name}\n')*2)
        with self.assertRaisesRegex(ValueError,'exactly once'):
            self.candidate()

    def test_source_and_monotonic_version_contracts(self):
        target=copy.deepcopy(self.target);target['sourceCommit']='c'*40
        with self.assertRaisesRegex(ValueError,'reviewed source'):
            self.candidate(target=target)
        self.assertTrue(stage.newer('0.2.0-alpha.10','0.2.0-alpha.9'))
        self.assertTrue(stage.newer('0.2.0','0.2.0-alpha.99'))
        self.assertFalse(stage.newer('0.2.0-alpha.2','0.2.0-alpha.3'))
        for field,value in [('version',self.current['version']),('build',5),('msiVersion','0.2.2')]:
            target=copy.deepcopy(self.target);target[field]=value
            with self.subTest(regression=field), self.assertRaisesRegex(ValueError,'must increase'):
                stage.progression(self.current,target)
        for invalid in ['0.2.0-alpha.01','256.0.0','0.1.65536']:
            with self.subTest(version=invalid), self.assertRaises(ValueError):
                stage.msi_version(invalid)

    def test_final_payload_cannot_select_other_bytes_app_host_notes_or_channel(self):
        now=datetime.now(timezone.utc)
        payload={'schema':1,'appID':stage.APP_ID,'generatedAt':now.isoformat(),'expiresAt':(now+timedelta(days=1)).isoformat(),'releases':[stage.release_entry(self.target,self.policy,self.msi,'Owner-reviewed notes','alpha')]}
        stage.payload_check(payload,self.target,self.policy,self.msi,'Owner-reviewed notes','alpha',now)
        for field,value in [('url','https://evil.test/installer.msi'),('sha256','0'*64),('signerSHA256','0'*64)]:
            changed=copy.deepcopy(payload);changed['releases'][0]['windowsX64'][field]=value
            with self.subTest(asset=field), self.assertRaisesRegex(ValueError,'verified target'):
                stage.payload_check(changed,self.target,self.policy,self.msi,'Owner-reviewed notes','alpha',now)
        for field,value in [('appID','other.app'),('schema',True)]:
            changed=copy.deepcopy(payload);changed[field]=value
            with self.subTest(identity=field), self.assertRaisesRegex(ValueError,'identity/schema'):
                stage.payload_check(changed,self.target,self.policy,self.msi,'Owner-reviewed notes','alpha',now)
        with self.assertRaisesRegex(ValueError,'Invalid release channel'):
            stage.release_entry(self.target,self.policy,self.msi,'notes','stable')
        with self.assertRaisesRegex(ValueError,'verified target'):
            stage.payload_check(payload,self.target,self.policy,self.msi,'Changed notes','alpha',now)

    def test_expiry_and_stage_location_are_fail_closed(self):
        now=datetime.now(timezone.utc)
        for generated,expires in [(now,now),(now,now+timedelta(days=32)),(now+timedelta(minutes=6),now+timedelta(days=1))]:
            with self.assertRaises(ValueError):
                stage.time_check(generated.isoformat(),expires.isoformat(),now)
        with self.assertRaisesRegex(ValueError,'outside the repository'):
            stage.output_directory(stage.REPO/'updates'/'must-not-be-written')

    @unittest.skipIf(os.name=='nt','Mac/Linux specifically prove claims cannot replace native validation')
    def test_non_windows_cannot_stage_claimed_authenticode(self):
        with self.assertRaisesRegex(ValueError,'native Windows'):
            stage.native_probe(self.msi)

    def test_rfc8032_public_signature_and_changed_exact_bytes(self):
        openssl=shutil.which('openssl')
        if not openssl:
            self.skipTest('OpenSSL3 verification executable unavailable')
        signature=bytes.fromhex('6291d657deec24024827e69c3abe01a30ce548a284743a445e3680d7db5ac3ac18ff9b538d16f290ae67f760984dc6594a7c15e9716ed28dc027beceea1ec40a')
        stage.verify_signature(self.public,bytes.fromhex('af82'),signature,openssl)
        with self.assertRaisesRegex(ValueError,'signature is invalid'):
            stage.verify_signature(self.public,bytes.fromhex('af83'),signature,openssl)


if __name__=='__main__':
    unittest.main()
