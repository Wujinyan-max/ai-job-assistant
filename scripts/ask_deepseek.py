#!/usr/bin/env python3
"""Run a read-only Codex job with a provider stored in CC Switch."""

from __future__ import annotations

import argparse
import json
import os
import shlex
import sqlite3
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from pathlib import Path


DEFAULT_PROVIDER = "公司01"
DEFAULT_MAX_CHARS = 1500


class AskDeepSeekError(Exception):
    """Expected user-facing failure."""

    def __init__(self, message: str, exit_code: int = 1):
        super().__init__(message)
        self.exit_code = exit_code


@dataclass(frozen=True)
class ProviderConfig:
    config_toml: str
    auth: dict[str, object]

    @property
    def secrets(self) -> tuple[str, ...]:
        values = []
        for value in self.auth.values():
            if isinstance(value, str) and value:
                values.append(value)
        return tuple(values)


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Use a CC Switch Codex provider for an isolated read-only job."
    )
    parser.add_argument("--task", required=True, help="The bounded read-only task to run")
    parser.add_argument("--provider", default=DEFAULT_PROVIDER, help="CC Switch provider name")
    parser.add_argument(
        "--max-chars",
        type=int,
        default=DEFAULT_MAX_CHARS,
        help="Maximum characters kept in the handoff",
    )
    parser.add_argument(
        "--output",
        default=".ai-handoff/deepseek-result.md",
        help="Handoff file path, relative to the current project by default",
    )
    parser.add_argument(
        "--cc-switch-db",
        type=Path,
        default=Path.home() / ".cc-switch" / "cc-switch.db",
        help=argparse.SUPPRESS,
    )
    args = parser.parse_args(argv)
    if args.max_chars < 1:
        parser.error("--max-chars must be greater than zero")
    return args


def load_provider(database: Path, provider_name: str) -> ProviderConfig:
    if not database.is_file():
        raise AskDeepSeekError(f"CC Switch 数据库不存在：{database}")

    try:
        connection = sqlite3.connect(f"file:{database.as_posix()}?mode=ro", uri=True)
        try:
            row = connection.execute(
                """
                SELECT settings_config
                FROM providers
                WHERE app_type = 'codex' AND name = ?
                LIMIT 1
                """,
                (provider_name,),
            ).fetchone()
        finally:
            connection.close()
    except sqlite3.Error as exc:
        raise AskDeepSeekError(f"读取 CC Switch 数据库失败：{exc}") from exc

    if row is None:
        raise AskDeepSeekError(f"CC Switch 的 Codex 标签下找不到供应商：{provider_name}")

    try:
        settings = json.loads(row[0])
        config_toml = settings["config"]
        auth = settings["auth"]
    except (KeyError, TypeError, json.JSONDecodeError) as exc:
        raise AskDeepSeekError(f"供应商 {provider_name} 的配置格式无法识别") from exc

    if not isinstance(config_toml, str) or not config_toml.strip():
        raise AskDeepSeekError(f"供应商 {provider_name} 缺少 Codex 配置")
    if not isinstance(auth, dict) or not auth:
        raise AskDeepSeekError(f"供应商 {provider_name} 缺少认证配置")
    return ProviderConfig(config_toml=config_toml, auth=auth)


def codex_command() -> list[str]:
    configured = os.environ.get("ASK_DEEPSEEK_CODEX_COMMAND", "codex")
    if configured.lstrip().startswith("["):
        try:
            command = json.loads(configured)
        except json.JSONDecodeError as exc:
            raise AskDeepSeekError("ASK_DEEPSEEK_CODEX_COMMAND 不是有效的 JSON 数组") from exc
        if not isinstance(command, list) or not command or not all(
            isinstance(part, str) and part for part in command
        ):
            raise AskDeepSeekError("ASK_DEEPSEEK_CODEX_COMMAND 必须是非空字符串数组")
        return command
    command = shlex.split(configured, posix=os.name != "nt")
    if not command:
        raise AskDeepSeekError("ASK_DEEPSEEK_CODEX_COMMAND 不能为空")
    return command


def redact(text: str, secrets: tuple[str, ...]) -> str:
    for secret in sorted(secrets, key=len, reverse=True):
        text = text.replace(secret, "[REDACTED]")
    return text


def build_prompt(task: str, max_chars: int) -> str:
    return f"""你是辅助分析代理，只能做只读工作。

任务：{task}

约束：
1. 禁止创建、修改、移动或删除任何项目文件。
2. 禁止执行会改变 Git、数据库、依赖或系统状态的命令。
3. 只返回结论、证据以及必要的文件路径和行号，不要粘贴完整源码。
4. 不确定的地方明确标注，不要猜测。
5. 最终答复不超过 {max_chars} 个字符。
"""


def write_private(path: Path, content: str) -> None:
    path.write_text(content, encoding="utf-8")
    try:
        path.chmod(0o600)
    except OSError:
        pass


def write_handoff(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(
            mode="w",
            encoding="utf-8",
            dir=path.parent,
            prefix=f".{path.name}.",
            suffix=".tmp",
            delete=False,
        ) as handle:
            handle.write(content)
            temporary = Path(handle.name)
        temporary.replace(path)
    finally:
        if temporary is not None and temporary.exists():
            temporary.unlink()


def run_job(args: argparse.Namespace) -> Path:
    project_root = Path.cwd().resolve()
    output_path = Path(args.output)
    if not output_path.is_absolute():
        output_path = project_root / output_path

    provider = load_provider(args.cc_switch_db.resolve(), args.provider)
    with tempfile.TemporaryDirectory(prefix="ask-deepseek-") as temporary_home_value:
        temporary_home = Path(temporary_home_value)
        write_private(temporary_home / "config.toml", provider.config_toml)
        write_private(
            temporary_home / "auth.json",
            json.dumps(provider.auth, ensure_ascii=False),
        )
        result_file = temporary_home / "result.md"
        command = [
            *codex_command(),
            "-a",
            "never",
            "exec",
            "--ephemeral",
            "--sandbox",
            "read-only",
            "-C",
            str(project_root),
            "-o",
            str(result_file),
            build_prompt(args.task, args.max_chars),
        ]
        environment = os.environ.copy()
        environment["CODEX_HOME"] = str(temporary_home)

        try:
            completed = subprocess.run(
                command,
                cwd=project_root,
                env=environment,
                capture_output=True,
                text=True,
                encoding="utf-8",
                errors="replace",
                check=False,
            )
        except FileNotFoundError as exc:
            raise AskDeepSeekError("找不到 codex 命令，请先安装 Codex CLI") from exc

        if completed.returncode != 0:
            details = redact(completed.stderr.strip() or completed.stdout.strip(), provider.secrets)
            if details:
                print(details, file=sys.stderr)
            raise AskDeepSeekError(
                f"DeepSeek 辅助任务执行失败（Codex 退出码 {completed.returncode}）",
                exit_code=completed.returncode,
            )
        if not result_file.is_file():
            raise AskDeepSeekError("Codex 未生成辅助任务结果")

        content = redact(result_file.read_text(encoding="utf-8"), provider.secrets)
        content = content.strip()[: args.max_chars]
        if not content:
            raise AskDeepSeekError("DeepSeek 返回了空结果")
        write_handoff(output_path, content)

    return output_path


def main(argv: list[str] | None = None) -> int:
    try:
        args = parse_args(argv)
        output_path = run_job(args)
    except AskDeepSeekError as exc:
        print(f"ask_deepseek: {exc}", file=sys.stderr)
        return exc.exit_code
    print(f"DeepSeek 交接结果已写入：{output_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
