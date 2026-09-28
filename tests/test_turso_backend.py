import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from server.kitty import Store, TursoRow


class TursoBackendTests(unittest.TestCase):
    def test_row_factory_preserves_sqlite_row_access(self):
        row = TursoRow(["id", "text"], (7, "hello"))
        self.assertEqual(row[0], 7)
        self.assertEqual(row["text"], "hello")
        self.assertEqual(dict(row), {"id": 7, "text": "hello"})

    def test_database_url_and_token_must_be_paired(self):
        with tempfile.TemporaryDirectory() as temp:
            with patch.dict(os.environ, {"TURSO_DATABASE_URL": "libsql://example", "TURSO_AUTH_TOKEN": ""}, clear=False):
                with self.assertRaises(ValueError):
                    Store(Path(temp) / "kitty.sqlite3")


if __name__ == "__main__":
    unittest.main()
