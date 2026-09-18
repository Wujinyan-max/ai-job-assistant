import json
import os
import sqlite3
import subprocess
import sys
import tempfile
import textwrap
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[2]
SCRIPT = PROJECT_ROOT / "scripts" / "ask_deepseek.py"


class AskDeepSeekIntegrationTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        self.db_path = self.root / "cc-switch.db"
        self.capture_path = self.root / "capture.json"
        self.output_path = self.root / "handoff.md"
        self.fake_codex = self.root / "fake_codex.py"
        self.secret = "sk-company-01-secret"
        self._create_database()
        self._create_fake_codex()

    def tearDown(self):
        self.temp_dir.cleanup()

    def _create_database(self):
        connection = sqlite3.connect(self.db_path)
        connection.execute(
            """
            CREATE TABLE providers (
                id TEXT NOT NULL,
                app_type TEXT NOT NULL,
                name TEXT NOT NULL,
                settings_config TEXT NOT NULL,
                meta TEXT NOT NULL DEFAULT '{}',
                is_current INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (id, app_type)
            )
            """
        )
        settings = {
            "auth": {"OPENAI_API_KEY": self.secret},
            "config": textwrap.dedent(
                """
                model_provider = "custom"
                model = "aliy/deepseek-v4.1-flash"

                [model_providers.custom]
                name = "custom"
                wire_api = "responses"
                base_url = "http://company.example/v1"
                """
            ).strip(),
        }
        connection.execute(
            "INSERT INTO providers (id, app_type, name, settings_config) VALUES (?, ?, ?, ?)",
            ("company-01", "codex", "公司01", json.dumps(settings, ensure_ascii=False)),
        )
        connection.commit()
        connection.close()

    def _create_fake_codex(self):
        self.fake_codex.write_text(
            textwrap.dedent(
                """
                import json
                import os
                import sys
                from pathlib import Path

                args = sys.argv[1:]
                codex_home = Path(os.environ["CODEX_HOME"])
                capture = {
                    "args": args,
                    "codex_home": str(codex_home),
                    "config": (codex_home / "config.toml").read_text(encoding="utf-8"),
                    "auth": json.loads((codex_home / "auth.json").read_text(encoding="utf-8")),
                }
                Path(os.environ["FAKE_CODEX_CAPTURE"]).write_text(
                    json.dumps(capture, ensure_ascii=False), encoding="utf-8"
                )

                if os.environ.get("FAKE_CODEX_FAIL") == "1":
                    print(capture["auth"]["OPENAI_API_KEY"], file=sys.stderr)
                    raise SystemExit(9)

                output_path = Path(args[args.index("-o") + 1])
                output_path.write_text("分析结果：" + "甲" * 40, encoding="utf-8")
                """
            ).strip(),
            encoding="utf-8",
        )

    def _run(self, *extra_args, env_overrides=None):
        env = os.environ.copy()
        env["ASK_DEEPSEEK_CODEX_COMMAND"] = json.dumps(
            [sys.executable, str(self.fake_codex)], ensure_ascii=False
        )
        env["FAKE_CODEX_CAPTURE"] = str(self.capture_path)
        env["PYTHONIOENCODING"] = "utf-8"
        if env_overrides:
            env.update(env_overrides)
        return subprocess.run(
            [
                sys.executable,
                str(SCRIPT),
                "--task",
                "定位登录超时调用链",
                "--cc-switch-db",
                str(self.db_path),
                "--output",
                str(self.output_path),
                *extra_args,
            ],
            cwd=PROJECT_ROOT,
            env=env,
            capture_output=True,
            text=True,
            encoding="utf-8",
        )

    def test_runs_company_provider_in_isolated_read_only_codex_home(self):
        result = self._run("--max-chars", "20")

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("分析结果：" + "甲" * 15, self.output_path.read_text(encoding="utf-8"))
        capture = json.loads(self.capture_path.read_text(encoding="utf-8"))
        self.assertIn("--ephemeral", capture["args"])
        self.assertEqual("read-only", capture["args"][capture["args"].index("--sandbox") + 1])
        self.assertEqual("never", capture["args"][capture["args"].index("-a") + 1])
        self.assertIn("aliy/deepseek-v4.1-flash", capture["config"])
        self.assertEqual(self.secret, capture["auth"]["OPENAI_API_KEY"])
        self.assertFalse(Path(capture["codex_home"]).exists())
        self.assertNotIn(self.secret, result.stdout + result.stderr)

    def test_missing_provider_fails_without_creating_handoff(self):
        result = self._run("--provider", "不存在")

        self.assertNotEqual(0, result.returncode)
        self.assertIn("不存在", result.stderr)
        self.assertFalse(self.output_path.exists())

    def test_codex_failure_redacts_provider_secret(self):
        result = self._run(env_overrides={"FAKE_CODEX_FAIL": "1"})

        self.assertEqual(9, result.returncode)
        self.assertNotIn(self.secret, result.stdout + result.stderr)
        self.assertIn("[REDACTED]", result.stderr)
        self.assertFalse(self.output_path.exists())

    def test_windows_powershell_51_can_parse_wrapper(self):
        wrapper = PROJECT_ROOT / "scripts" / "ask-deepseek.ps1"
        escaped_wrapper = str(wrapper).replace("'", "''")
        command = (
            "$tokens = $null; $errors = $null; "
            f"[System.Management.Automation.Language.Parser]::ParseFile('{escaped_wrapper}', "
            "[ref]$tokens, [ref]$errors) | Out-Null; "
            "if ($errors.Count -gt 0) { exit 1 }"
        )

        result = subprocess.run(
            ["powershell", "-NoProfile", "-Command", command],
            capture_output=True,
            check=False,
        )

        self.assertEqual(0, result.returncode, result.stderr.decode(errors="replace"))


if __name__ == "__main__":
    unittest.main()
