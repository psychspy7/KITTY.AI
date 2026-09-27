import importlib.util
import io
from pathlib import Path
import tarfile
import tempfile
import unittest
spec=importlib.util.spec_from_file_location('phone',Path(__file__).resolve().parents[1]/'tools/update_phone.py');phone=importlib.util.module_from_spec(spec);spec.loader.exec_module(phone)
class PhoneBackupTest(unittest.TestCase):
    def test_restore_keeps_model_and_settings_but_removes_dead_keystore_token(self):
        with tempfile.TemporaryDirectory() as tmp:
            source=Path(tmp)/'in.tar';out=Path(tmp)/'out.tar'
            with tarfile.open(source,'w') as archive:
                for name,data in [('files/vosk-model/am/final.mdl',b'model'),('shared_prefs/kitty.xml',b'<map><string name="token">ciphertext</string><string name="iv">iv</string><string name="url">http://127.0.0.1:8765</string></map>')]:
                    info=tarfile.TarInfo(name);info.size=len(data);archive.addfile(info,io.BytesIO(data))
            phone.sanitize_backup(source,out)
            with tarfile.open(out) as archive:
                self.assertEqual(archive.extractfile('files/vosk-model/am/final.mdl').read(),b'model')
                xml=archive.extractfile('shared_prefs/kitty.xml').read();self.assertNotIn(b'ciphertext',xml);self.assertIn(b'http://127.0.0.1:8765',xml)
    def test_traversal_is_rejected_before_uninstall(self):
        with tempfile.TemporaryDirectory() as tmp:
            source=Path(tmp)/'in.tar'
            with tarfile.open(source,'w') as archive:archive.addfile(tarfile.TarInfo('../other-app'))
            with self.assertRaises(ValueError):phone.sanitize_backup(source,Path(tmp)/'out.tar')
