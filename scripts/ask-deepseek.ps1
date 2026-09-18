param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$Task,

    [ValidateNotNullOrEmpty()]
    [string]$Provider = "公司01",

    [ValidateRange(1, 100000)]
    [int]$MaxChars = 1500,

    [ValidateNotNullOrEmpty()]
    [string]$Output = ".ai-handoff/deepseek-result.md"
)

$ErrorActionPreference = "Stop"
$python = Get-Command python -ErrorAction SilentlyContinue
if (-not $python) {
    throw "找不到 Python。请先安装 Python 3，并确保 python 命令已加入 PATH。"
}

$scriptPath = Join-Path $PSScriptRoot "ask_deepseek.py"
& $python.Source $scriptPath `
    --task $Task `
    --provider $Provider `
    --max-chars $MaxChars `
    --output $Output

exit $LASTEXITCODE
