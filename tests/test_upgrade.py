import importlib.util
import json
from pathlib import Path
import sqlite3
import tempfile
import unittest
spec=importlib.util.spec_from_file_location('upgrade',Path(__file__).resolve().parents[1]/'tools/optimize_low_memory.py');upgrade=importlib.util.module_from_spec(spec);spec.loader.exec_module(upgrade)
class UpgradeTest(unittest.TestCase):
    def test_profile_preserves_token_custom_personality_and_database(self):
        with tempfile.TemporaryDirectory() as tmp:
            home=Path(tmp);(home/'config.json').write_text(json.dumps({'token':'keep-me','history_days':30,'weather_city':'Delhi'}));(home/'personality.txt').write_text('Keep my custom personality, exactly.')
            with sqlite3.connect(home/'kitty.sqlite3') as c:c.execute('CREATE TABLE marker(value)');c.execute('INSERT INTO marker VALUES(42)')
            backup=upgrade.upgrade(home);config=json.loads((home/'config.json').read_text())
            self.assertEqual(config['token'],'keep-me');self.assertEqual(config['weather_city'],'Delhi');self.assertEqual(config['history_days'],0)
            self.assertIn('Keep my custom personality, exactly.',(home/'personality.txt').read_text());self.assertEqual((backup/'personality.txt').read_text(),'Keep my custom personality, exactly.')
            with sqlite3.connect(backup/'kitty.sqlite3') as c:self.assertEqual(c.execute('SELECT value FROM marker').fetchone()[0],42)
            upgrade.upgrade(home);self.assertEqual((home/'personality.txt').read_text().count('Project identity:'),1)
