# Project agent instructions

## DeepSeek delegation

The primary GPT agent may use `scripts/ask-deepseek.ps1` for small, bounded,
read-only investigations when doing so saves GPT context.

Suitable tasks:

- repository search and file inventory
- call-chain and dependency summaries
- log or test-failure clustering
- candidate test cases and documentation outlines
- identifying likely files and line numbers for a change

Rules:

- Keep architecture decisions, security-sensitive work, migrations, final code
  edits, and final verification with the primary GPT agent.
- Do not ask DeepSeek to edit, create, move, or delete project files.
- Give DeepSeek one concrete question at a time.
- Keep `-MaxChars` between 800 and 1500 unless the user requests otherwise.
- Require evidence as file paths and line numbers; do not request full source
  file copies.
- Independently verify DeepSeek's conclusions before changing code.
- Never include credentials, tokens, private user data, or unrelated files in a
  delegated task.
- A delegated session launched by this tool must never invoke the tool again.
- Treat `.ai-handoff/deepseek-result.md` as disposable local output.

Example:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/ask-deepseek.ps1 `
  -Task "只读定位登录超时的调用链，返回文件路径、行号和最多 5 条结论" `
  -MaxChars 1200
```
