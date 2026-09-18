<#
  职得 JobPath —— Cloudflare Tunnel 一键脚本

  作用：把本机网关（默认 8090）通过 Cloudflare Tunnel 暴露到公网。
        相比 cpolar 免费版：地址固定、免公网 IP、自带 HTTPS、不限速。

  前提：需要一个「NS 已托管到 Cloudflare」的域名（免费套餐即可）。

  用法：
    # 1) 一次性配置：登录 + 建隧道 + 写配置 + 解析域名
    powershell -ExecutionPolicy Bypass -File cloudflare-tunnel.ps1 -Action setup -Domain job.你的域名.com

    # 2) 免域名快速试跑（临时地址，无需域名）
    powershell -ExecutionPolicy Bypass -File cloudflare-tunnel.ps1 -Action quick

    # 3) 启动隧道（前台运行，关窗口即停）
    powershell -ExecutionPolicy Bypass -File cloudflare-tunnel.ps1 -Action run

    # 4) 查看状态
    powershell -ExecutionPolicy Bypass -File cloudflare-tunnel.ps1 -Action status

    # 5) 装成 Windows 服务（开机自启，需管理员）
    powershell -ExecutionPolicy Bypass -File cloudflare-tunnel.ps1 -Action install-service
#>
param(
    [ValidateSet('setup', 'quick', 'run', 'status', 'install-service', 'uninstall-service')]
    [string]$Action = 'run',

    [string]$Domain = '',
    [string]$TunnelName = 'jobpath',
    [int]$Port = 8090,
    [string]$Cloudflared = ''
)

$ErrorActionPreference = 'Stop'

function Write-Step($t) { Write-Host $t -ForegroundColor Yellow }
function Write-Ok($t)   { Write-Host $t -ForegroundColor Green }
function Write-Tip($t)  { Write-Host $t -ForegroundColor DarkGray }

function Resolve-Cloudflared {
    param([string]$Hint)
    if ($Hint) {
        if (Test-Path $Hint) { return $Hint }
        throw "指定的 cloudflared 不存在：$Hint"
    }
    $cmd = Get-Command cloudflared -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    foreach ($p in @(
        'C:\Program Files (x86)\cloudflared\cloudflared.exe',
        'C:\Program Files\cloudflared\cloudflared.exe',
        "$env:USERPROFILE\cloudflared\cloudflared.exe"
    )) {
        if (Test-Path $p) { return $p }
    }
    throw '找不到 cloudflared.exe，请先安装：winget install --id Cloudflare.cloudflared'
}

$cf        = Resolve-Cloudflared -Hint $Cloudflared
$cfDir     = Join-Path $env:USERPROFILE '.cloudflared'
$certPath  = Join-Path $cfDir 'cert.pem'
$configPath = Join-Path $cfDir 'config.yml'

function Assert-LoggedIn {
    if (Test-Path $certPath) { return }
    Write-Step '尚未登录 Cloudflare，即将打开浏览器授权...'
    Write-Tip  '  请在浏览器里选中要用的域名，点 Authorize（没有域名就先在 Cloudflare 添加站点）'
    & $cf tunnel login
    if (-not (Test-Path $certPath)) { throw '登录未完成，未生成 cert.pem' }
}

function Get-TunnelByName($name) {
    $raw = & $cf tunnel list --output json 2>$null
    if (-not $raw) { return $null }
    return ($raw | ConvertFrom-Json) | Where-Object { $_.name -eq $name } | Select-Object -First 1
}

switch ($Action) {

    # ------------------------------------------------------------ 一次性配置
    'setup' {
        if (-not $Domain) { throw '请用 -Domain 指定域名，例如：-Domain job.example.com' }
        Assert-LoggedIn
        New-Item -ItemType Directory -Force -Path $cfDir | Out-Null

        $tunnel = Get-TunnelByName $TunnelName
        if (-not $tunnel) {
            Write-Step "创建隧道 $TunnelName ..."
            & $cf tunnel create $TunnelName | Out-Null
            $tunnel = Get-TunnelByName $TunnelName
        }
        if (-not $tunnel) { throw '隧道创建失败，请检查上面的错误输出' }
        Write-Ok "隧道就绪：$TunnelName  (id: $($tunnel.id))"

        $credPath = Join-Path $cfDir "$($tunnel.id).json"
        if (-not (Test-Path $credPath)) { throw "缺少凭据文件：$credPath" }

        $yaml = @"
tunnel: $($tunnel.id)
credentials-file: $($credPath -replace '\\', '/')
protocol: quic
no-autoupdate: true

ingress:
  - hostname: $Domain
    service: http://localhost:$Port
    originRequest:
      connectTimeout: 30s
  - service: http_status:404
"@
        $utf8 = New-Object System.Text.UTF8Encoding($false)
        [System.IO.File]::WriteAllText($configPath, $yaml, $utf8)
        Write-Ok "配置已写入：$configPath"

        Write-Step "解析域名 $Domain 到隧道 ..."
        try {
            & $cf tunnel route dns $TunnelName $Domain
            Write-Ok "DNS 已创建：$Domain"
        } catch {
            Write-Tip "  DNS 记录可能已存在，可手动在 Cloudflare 面板确认 CNAME 指向 $($tunnel.id).cfargotunnel.com"
        }

        Write-Host ''
        Write-Ok '配置完成，接着启动隧道：'
        Write-Host "  powershell -ExecutionPolicy Bypass -File cloudflare-tunnel.ps1 -Action run" -ForegroundColor Cyan
        Write-Host ''
    }

    # -------------------------------------------------- 免域名快速隧道
    'quick' {
        $log = Join-Path (Split-Path -Parent $PSCommandPath) 'output\tunnel-quick.log'
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $log) | Out-Null
        Write-Step "启动临时隧道（trycloudflare.com）→ http://localhost:$Port ..."
        Write-Tip  '  地址形如 https://xxx.trycloudflare.com，重启后会变；要固定地址请用 -Action setup -Domain 你的域名'
        Write-Host ''
        & $cf tunnel --url "http://localhost:$Port" --no-autoupdate --logfile "$log" --loglevel info
    }

    # ---------------------------------------------------------------- 启动
    'run' {
        if (-not (Test-Path $configPath)) {
            throw "还没配置。请先执行：-Action setup -Domain 你的域名"
        }
        Write-Ok "隧道启动中：https 域名 → http://localhost:$Port"
        Write-Tip  '  保持本窗口开着；要后台常驻请用 -Action install-service'
        & $cf --config $configPath tunnel run $TunnelName
    }

    # ---------------------------------------------------------------- 状态
    'status' {
        if (Test-Path $certPath) {
            Write-Step '隧道列表：'
            & $cf tunnel list
        } else {
            Write-Tip '尚未登录 Cloudflare（未生成 cert.pem），无法列出隧道'
        }
        Write-Host ''
        $procs = Get-CimInstance Win32_Process -Filter "Name='cloudflared.exe'" -ErrorAction SilentlyContinue |
            Where-Object { $_.CommandLine -match 'tunnel (run|--url|--config)' }
        if ($procs) { Write-Ok "隧道进程运行中，PID：$($procs.ProcessId -join ', ')" } else { Write-Tip '隧道进程未运行' }
        $svc = Get-Service cloudflared -ErrorAction SilentlyContinue
        if ($svc) { Write-Ok "Windows 服务 cloudflared：$($svc.Status)" } else { Write-Tip 'Windows 服务未安装（可选，用 -Action install-service）' }
        if (Test-Path $configPath) { Write-Tip "配置文件：$configPath" }
    }

    # ------------------------------------------------------------ 安装服务
    'install-service' {
        $isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
            ).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
        if (-not $isAdmin) { throw '安装服务需要管理员权限，请用「以管理员身份运行」重开 PowerShell' }
        if (-not (Test-Path $configPath)) { throw '还没配置，请先执行 -Action setup -Domain 你的域名' }
        Assert-LoggedIn
        & $cf --config $configPath service install
        & $cf --config $configPath service start
        Write-Ok '服务已安装并启动，开机会自动拉起隧道'
    }

    # ------------------------------------------------------------ 卸载服务
    'uninstall-service' {
        $isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
            ).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
        if (-not $isAdmin) { throw '卸载服务需要管理员权限' }
        & $cf --config $configPath service uninstall
        Write-Ok '服务已卸载'
    }
}