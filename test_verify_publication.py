"""Negative controls for the standalone publication checker; no product imports."""
import hashlib
import importlib.util
import json
import shutil
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('publication_check', ROOT/'verify_publication.py')
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)


class PublicationChecks(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.copy = Path(self.temp.name)/'copy'
        shutil.copytree(ROOT, self.copy, ignore=shutil.ignore_patterns('.git','__pycache__'))

    def rejected(self):
        with self.assertRaises((ValueError, AssertionError, KeyError, OSError)):
            checker.verify(self.copy)

    def test_pristine(self):
        self.assertEqual(checker.verify(self.copy)['status'],'PASS')

    def test_tampered_file(self):
        path=self.copy/'NOTICE'
        path.write_bytes(path.read_bytes()+b'changed')
        self.rejected()

    def test_missing_file(self):
        (self.copy/'NOTICE').unlink()
        self.rejected()

    def test_extra_file(self):
        (self.copy/'unexpected.txt').write_text('unexpected')
        self.rejected()

    def test_duplicate_json_key(self):
        path=self.copy/'MANIFEST.json'
        path.write_text('{"files":{},"files":{}}')
        self.rejected()

    def test_unsafe_manifest_path(self):
        path=self.copy/'MANIFEST.json'
        data=json.loads(path.read_text())
        data['files']['../outside']={'bytes':0,'sha256':'0'*64}
        path.write_text(json.dumps(data))
        self.rejected()

    def test_resealed_invalid_source_binding_or_measurement(self):
        label=self.copy/'cases/original/labels.json'
        if label.exists():
            data=json.loads(label.read_text())
            data['applicationCallSites'][0]['location']['line']=9999999
        else:
            label=self.copy/'results/library-001/13.3.0/results.json'
            data=json.loads(label.read_text())
            data['roundTrips']=9999
        label.write_text(json.dumps(data))
        manifest=self.copy/'MANIFEST.json'
        declared=json.loads(manifest.read_text())
        raw=label.read_bytes()
        declared['files'][label.relative_to(self.copy).as_posix()]={
            'bytes':len(raw),'sha256':hashlib.sha256(raw).hexdigest()}
        manifest.write_text(json.dumps(declared))
        self.rejected()


if __name__=='__main__':
    unittest.main(verbosity=2)
