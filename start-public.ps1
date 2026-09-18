# ============================================================================
#  职得 JobPath 一键启动（本机 + 内网穿透）
#
#  作用：打包前端 → 启动网关（静态资源 + API 代理）→ 开启 cpolar 隧道，
#        最后打印公网访问地址，直接发给别人就能用。
#
#  用法：右键「使用 PowerShell 运行」，或
#        powershell -ExecutionPolicy Bypass -File start-public.ps1
# ============================================================================
param(
    [int]$Port = 8090,
    [string]$Backend = 'http://127.0.0.1:8088',
    [string]$Cpolar = 'E:\cpolar\cpolar.exe',
    # 隧道方式：cloudflare（推荐，地址固定且自带 HTTPS）/ cpolar / none
    [ValidateSet('cloudflare', 'cpolar', 'none')]
    [string]$Tunnel = 'cloudflare',
    # 已配置固定域名时填这里，走命名隧道；留空则用 trycloudflare 临时地址
    [string]$Domain = ''
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSCommandPath
$frontend = Join-Path $root 'frontend'
$logDir = Join-Path $root 'output'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

Write-Host ''
Write-Host '=== 职得 JobPath 启动中 ===' -ForegroundColor Cyan

# ---------------------------------------------------------------- 1. 检查后端
Write-Host '[1/4] 检查后端服务...' -ForegroundColor Yellow
try {
    Invoke-WebRequest -Uri "$Backend/api/doc.html" -UseBasicParsing -TimeoutSec 3 | Out-Null
    Write-Host '      后端正常' -ForegroundColor Green
} catch {
    Write-Host '      后端未启动，请先运行 Spring Boot 项目（端口 8088）' -ForegroundColor Red
    exit 1
}

# ---------------------------------------------------------------- 2. 打包前端
Write-Host '[2/4] 打包前端...' -ForegroundColor Yellow
$buildLog = Join-Path $logDir 'build.log'
$viteJs = Join-Path $frontend 'node_modules\vite\bin\vite.js'
$proc = Start-Process -FilePath 'node' -ArgumentList $viteJs, 'build' `
    -WorkingDirectory $frontend -WindowStyle Hidden -Wait -PassThru `
    -RedirectStandardOutput $buildLog -RedirectStandardError "$buildLog.err"
if ($proc.ExitCode -ne 0 -or -not (Test-Path (Join-Path $frontend 'dist'))) {
    Write-Host '      构建失败，详情见 output/build.log.err' -ForegroundColor Red
    exit 1
}
Write-Host '      构建完成' -ForegroundColor Green

# ---------------------------------------------------------------- 3. 启动网关
Write-Host '[3/4] 启动网关服务...' -ForegroundColor Yellow
$gatewayLog = Join-Path $logDir 'gateway.log'
Get-CimInstance Win32_Process -Filter "Name='node.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -like '*serve.cjs*' } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }

Start-Process -FilePath 'node' `
    -ArgumentList (Join-Path $frontend 'serve.cjs'), $Port `
    -WorkingDirectory $frontend -WindowStyle Hidden `
    -RedirectStandardOutput $gatewayLog -RedirectStandardError "$gatewayLog.err"
Start-Sleep -Seconds 3

try {
    Invoke-WebRequest -Uri "http://localhost:$Port/" -UseBasicParsing -TimeoutSec 5 | Out-Null
    Write-Host "      网关已启动：http://localhost:$Port" -ForegroundColor Green
} catch {
    Write-Host '      网关启动失败，请查看 output/gateway.log.err' -ForegroundColor Red
    exit 1
}

# ---------------------------------------------------------------- 4. 开启隧道
Write-Host "[4/4] 开启内网穿透（$Tunnel）..." -ForegroundColor Yellow
$publicUrl = $null

if ($Tunnel -eq 'cloudflare') {
    # ---------------------------------------------------------- Cloudflare Tunnel
    $cf = (Get-Command cloudflared -ErrorAction SilentlyContinue).Source
    if (-not $cf) {
        foreach ($p in @('C:\Program Files (x86)\cloudflared\cloudflared.exe',
                         'C:\Program Files\cloudflared\cloudflared.exe')) {
            if (Test-Path $p) { $cf = $p; break }
        }
    }
    if (-not $cf) {
        Write-Host '      未安装 cloudflared，请先执行：winget install --id Cloudflare.cloudflared' -ForegroundColor Red
        exit 1
    }

    # 已装成 Windows 服务时由服务托管隧道，不再自己起进程，免得把它杀掉
    $cfService = Get-Service cloudflared -ErrorAction SilentlyContinue
    if ($cfService) {
        if ($cfService.Status -ne 'Running') { Start-Service cloudflared -ErrorAction SilentlyContinue }
        if ((Get-Service cloudflared).Status -eq 'Running') {
            Write-Host '      cloudflared 服务运行中，已由服务托管隧道' -ForegroundColor Green
            $publicUrl = if ($Domain) { "https://$Domain" } else { $null }
            if (-not $publicUrl) { Write-Host '      提示：服务模式下请用 -Domain 你的域名 以显示访问地址' -ForegroundColor DarkGray }
        }
    }

    if (-not $cfService) {
        Get-CimInstance Win32_Process -Filter "Name='cloudflared.exe'" -ErrorAction SilentlyContinue |
            Where-Object { $_.CommandLine -match 'tunnel (run|--url|--config)' } |
            ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }

        $cfLog = Join-Path $logDir 'tunnel-quick.log'
        Remove-Item $cfLog -ErrorAction SilentlyContinue

        if ($Domain) {
            # 命名隧道：地址固定，需先跑 cloudflare-tunnel.ps1 -Action setup -Domain 你的域名
            $cfConfig = Join-Path $env:USERPROFILE '.cloudflared\config.yml'
            if (-not (Test-Path $cfConfig)) {
                Write-Host "      未找到 $cfConfig" -ForegroundColor Red
                Write-Host "      请先执行：cloudflare-tunnel.ps1 -Action setup -Domain $Domain" -ForegroundColor Red
                exit 1
            }
            Start-Process -FilePath $cf -ArgumentList '--config', $cfConfig, 'tunnel', 'run', 'jobpath',
                '--no-autoupdate', '--logfile', $cfLog, '--loglevel', 'info' -WindowStyle Hidden
            $publicUrl = "https://$Domain"
            Start-Sleep -Seconds 8
        } else {
            # 临时隧道：地址每次启动都变，适合先跑起来给别人试用
            Start-Process -FilePath $cf -ArgumentList 'tunnel', '--url', "http://localhost:$Port",
                '--no-autoupdate', '--logfile', $cfLog, '--loglevel', 'info' -WindowStyle Hidden
            for ($i = 0; $i -lt 30; $i++) {
                Start-Sleep -Seconds 1
                if (Test-Path $cfLog) {
                    $match = Select-String -Path $cfLog -Pattern 'https://[a-z0-9-]+\.trycloudflare\.com' -ErrorAction SilentlyContinue |
                        Select-Object -First 1
                    if ($match) { $publicUrl = $match.Matches[0].Value; break }
                }
            }
        }
    }
} elseif ($Tunnel -eq 'cpolar') {
$tunnelLog = Join-Path $logDir 'cpolar.log'
Remove-Item $tunnelLog, "$tunnelLog.err" -ErrorAction SilentlyContinue

Get-Process cpolar -ErrorAction SilentlyContinue |
    Where-Object { $_.Id -ne 0 } | ForEach-Object {
        # 只关掉我们自己起的临时隧道，服务版进程保留
        $cmd = (Get-CimInstance Win32_Process -Filter "ProcessId=$($_.Id)").CommandLine
        if ($cmd -like '*http*' -and $cmd -like "*$Port*") { Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue }
    }

Start-Process -FilePath $Cpolar -ArgumentList 'http', "$Port", '-log=stdout' `
    -WindowStyle Hidden -RedirectStandardOutput $tunnelLog -RedirectStandardError "$tunnelLog.err"

# 等待隧道建立并解析公网地址
for ($i = 0; $i -lt 30; $i++) {
    Start-Sleep -Seconds 1
    if (Test-Path $tunnelLog) {
        # 日志里的引号被 JSON 转义成了 \"，所以匹配时把反斜杠一起吃掉
        $match = Select-String -Path $tunnelLog -Pattern 'PublicUrl\\?":\\?"(https://[^\\"]+)' -ErrorAction SilentlyContinue |
            Select-Object -First 1
        if ($match) { $publicUrl = $match.Matches[0].Groups[1].Value; break }
    }
}
}

Write-Host ''
if ($Tunnel -eq 'none') {
    Write-Host '=====================================================' -ForegroundColor Green
    Write-Host '  启动成功（未开隧道，仅本机可访问）' -ForegroundColor Green
    Write-Host ''
    Write-Host "  http://localhost:$Port" -ForegroundColor Cyan
    Write-Host ''
    Write-Host '=====================================================' -ForegroundColor Green
} elseif ($publicUrl) {
    Write-Host '=====================================================' -ForegroundColor Green
    Write-Host '  启动成功！把下面的地址发给别人即可访问' -ForegroundColor Green
    Write-Host ''
    Write-Host "  $publicUrl" -ForegroundColor Cyan
    Write-Host ''
    Write-Host '=====================================================' -ForegroundColor Green
    Write-Host ''
    Write-Host '提示：' -ForegroundColor DarkGray
    Write-Host '  - 保持本机开机、后端服务运行，别人才能访问' -ForegroundColor DarkGray
    if ($Tunnel -eq 'cloudflare' -and -not $Domain) {
        Write-Host '  - 当前是 trycloudflare 临时地址，重启后会变' -ForegroundColor DarkGray
        Write-Host '    要固定地址：cloudflare-tunnel.ps1 -Action setup -Domain 你的域名' -ForegroundColor DarkGray
        Write-Host '    之后带 -Domain 你的域名 再跑本脚本即可（需域名 NS 托管在 Cloudflare）' -ForegroundColor DarkGray
    }
    if ($Tunnel -eq 'cpolar') {
        Write-Host '  - cpolar 免费版地址每隔一段时间会变化，重启后看这里的新地址' -ForegroundColor DarkGray
    }
    Write-Host '  - 关掉命令行窗口不影响服务运行' -ForegroundColor DarkGray
} else {
    Write-Host '隧道已启动，但未解析到公网地址，请查看 output/ 下的隧道日志' -ForegroundColor Yellow
}
Write-Host ''
