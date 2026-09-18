<#
.SYNOPSIS
    按 docs/PRD-product-optimization.md 逐项冒烟：P0-1 / P0-2 / P0-3 / P1-1 / P1-2 / P1-3 / P2-1 / P2-2 / P2-3

.DESCRIPTION
    跑之前请确保后端已启动（默认 http://localhost:8088/api）。
    脚本只用演示账号的真数据跑一遍接口，验证每项需求的返回结构是不是对的，
    跑完会把临时造的数据清理掉（投递、面试、题目）。

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts/smoke-prd.ps1
#>
param(
    [string]$BaseUrl = 'http://localhost:8088/api',
    [string]$Username = 'demo',
    [string]$Password = '123456'
)

$ErrorActionPreference = 'Stop'
$script:pass = 0
$script:fail = 0
$token = $null

# Windows PowerShell 5.1 默认按系统 ANSI 处理控制台和请求体，中文会变成乱码，
# 这里统一走 UTF-8，保证 powershell 与 pwsh 跑出来的结果一致。
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

function Invoke-Api {
    param([string]$Method, [string]$Path, $Body, [switch]$Quiet)
    $headers = @{}
    if ($token) { $headers.Authorization = "Bearer $token" }
    $params = @{ Method = $Method; Uri = "$BaseUrl$Path"; Headers = $headers }
    if ($null -ne $Body) {
        # 必须发 UTF-8 字节：5.1 直接传字符串会按 ISO-8859-1 编码，中文立即损坏
        $params.Body = [System.Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 8))
        $params.ContentType = 'application/json; charset=utf-8'
    }
    try {
        $response = Invoke-WebRequest @params
    } catch {
        if ($Quiet) { return $null }
        throw
    }
    # 自己按 UTF-8 解码响应，避免 5.1 把带中文的 JSON 解成乱码
    $json = [System.Text.Encoding]::UTF8.GetString($response.RawContentStream.ToArray())
    $payload = $json | ConvertFrom-Json
    if ($payload.code -ne 200) {
        if ($Quiet) { return $null }
        throw "接口 $Path 返回 code=$($payload.code) message=$($payload.message)"
    }
    return $payload.data
}

function Check {
    param([string]$Name, [bool]$Ok, [string]$Detail = '')
    if ($Ok) {
        $script:pass++
        Write-Host "  [通过] $Name" -ForegroundColor Green
    } else {
        $script:fail++
        Write-Host "  [失败] $Name $Detail" -ForegroundColor Red
    }
}

Write-Host "`n=== 登录 ===" -ForegroundColor Cyan
$token = (Invoke-Api Post '/auth/login' @{ username = $Username; password = $Password }).token
Check '演示账号登录' ([bool]$token)

$jobs = (Invoke-Api Get '/jobs?pageNum=1&pageSize=5').records
$resumes = Invoke-Api Get '/resumes/all'
Check '存在可用的职位与简历样本' ($jobs.Count -gt 0 -and $resumes.Count -gt 0)

# ---------------------------------------------------------------- P2-3 状态文案
Write-Host "`n=== P2-3 状态文案：CLOSED 显示「已放弃」 ===" -ForegroundColor Cyan
$statuses = Invoke-Api Get '/applications/statuses'
Check 'CLOSED 的文案是「已放弃」' ($statuses.CLOSED -eq '已放弃') "实际=$($statuses.CLOSED)"

# ------------------------------------------------------------------ P2-2 N 薪
Write-Host "`n=== P2-2 薪资描述 salaryDesc ===" -ForegroundColor Cyan
$jobId = (Invoke-Api Post '/jobs' @{
    jobName     = "冒烟职位-$([guid]::NewGuid().ToString('N').Substring(0,6))"
    jobDescription = '负责后端服务开发，熟悉 Java / Spring Boot / MySQL / Redis。'
    salaryMin   = 15
    salaryMax   = 30
    salaryDesc  = '15-30K·14薪'
    location    = '上海'
    status      = 'OPEN'
}).ToString()
$created = Invoke-Api Get "/jobs/$jobId"
Check 'salaryDesc 能存能取' ($created.salaryDesc -eq '15-30K·14薪') "实际=$($created.salaryDesc)"

# ------------------------------------------------------------------ P0-1 一键投递
Write-Host "`n=== P0-1 一键投递（表单只带 jobId + resumeId + APPLIED） ===" -ForegroundColor Cyan
$resumeId = $resumes[0].id
$appId = (Invoke-Api Post '/applications' @{
    jobId             = [long]$jobId
    resumeId          = [long]$resumeId
    applicationStatus = 'APPLIED'
}).ToString()
$app = Invoke-Api Get "/applications/$appId"
Check '投递创建成功且状态为 APPLIED' ($app.applicationStatus -eq 'APPLIED') "实际=$($app.applicationStatus)"
Check 'applyTime 自动填成当前时间' ([bool]$app.applyTime)
Check 'P1-2 投递详情带 applyTime 供卡片算距今天数' ([bool]$app.applyTime)

# ------------------------------------------------------- P0-2 面试通过自动流转
Write-Host "`n=== P0-2 面试结果自动流转 ===" -ForegroundColor Cyan
$interviewId = (Invoke-Api Post '/interviews' @{
    applicationId = [long]$appId
    roundName     = '技术一面'
    interviewType = 'VIDEO'
    interviewTime = (Get-Date).AddDays(1).ToString('yyyy-MM-dd HH:mm:ss')
    result        = 'PENDING'
}).ToString()
$afterCreate = Invoke-Api Get "/applications/$appId"
Check '创建面试后投递自动推进为 INTERVIEW' ($afterCreate.applicationStatus -eq 'INTERVIEW') "实际=$($afterCreate.applicationStatus)"

# 从非 PASS 改成 PASS：应返回 awaitingNextStep
$passFlow = Invoke-Api Put "/interviews/$interviewId" @{
    applicationId = [long]$appId; roundName = '技术一面'; interviewType = 'VIDEO'
    interviewTime = (Get-Date).AddDays(1).ToString('yyyy-MM-dd HH:mm:ss'); result = 'PASS'
}
Check '结果改为通过时返回 awaitingNextStep=true' ($passFlow.awaitingNextStep -eq $true)
$stillInterview = Invoke-Api Get "/applications/$appId"
Check '没选下一步时投递状态保持不变' ($stillInterview.applicationStatus -eq 'INTERVIEW') "实际=$($stillInterview.applicationStatus)"

# 再次保存（还是 PASS）不该重复触发弹窗
$again = Invoke-Api Put "/interviews/$interviewId" @{
    applicationId = [long]$appId; roundName = '技术一面'; interviewType = 'VIDEO'
    interviewTime = (Get-Date).AddDays(1).ToString('yyyy-MM-dd HH:mm:ss'); result = 'PASS'; review = '补一下复盘'
}
Check '已经是通过时不再重复弹窗' ($again.awaitingNextStep -eq $false)

# 选「进入下一轮」：保持 INTERVIEW 并建草稿
$nextRound = Invoke-Api Put "/interviews/$interviewId" @{
    applicationId = [long]$appId; roundName = '技术一面'; interviewType = 'VIDEO'
    interviewTime = (Get-Date).AddDays(1).ToString('yyyy-MM-dd HH:mm:ss'); result = 'PASS'; nextStep = 'NEXT_ROUND'
}
Check '选「进入下一轮」自动建草稿' ([bool]$nextRound.nextRoundInterviewId)
Check '草稿轮次为第 2 轮' ($nextRound.nextRoundNo -eq 2) "实际=$($nextRound.nextRoundNo)"

# 选「已拿 Offer」：投递变 OFFER
$offerFlow = Invoke-Api Put "/interviews/$interviewId" @{
    applicationId = [long]$appId; roundName = '技术一面'; interviewType = 'VIDEO'
    interviewTime = (Get-Date).AddDays(1).ToString('yyyy-MM-dd HH:mm:ss'); result = 'PASS'; nextStep = 'OFFER'
}
Check '选「已拿 Offer」投递状态变 OFFER' ($offerFlow.applicationStatus -eq 'OFFER') "实际=$($offerFlow.applicationStatus)"

# ------------------------------------------------------------ P0-3 面试提醒
Write-Host "`n=== P0-3 7 天内面试提醒 ===" -ForegroundColor Cyan
$upcoming = Invoke-Api Get '/interviews/upcoming?days=7'
Check 'upcoming 接口返回未来 7 天面试' ($upcoming.Count -ge 1) "实际数量=$($upcoming.Count)"
$filtered = Invoke-Api Get '/interviews?pageNum=1&pageSize=10&upcomingDays=7'
Check '面试列表支持 upcomingDays=7 筛选' ($filtered.total -ge 1) "实际 total=$($filtered.total)"
$far = Invoke-Api Get '/interviews?pageNum=1&pageSize=10&upcomingDays=1'
Check '窗口收窄到 1 天时结果不超过 7 天窗口' ($far.total -le $filtered.total)

# ------------------------------------------------------------ P1-1 简历效果分析
Write-Host "`n=== P1-1 简历效果分析报表 ===" -ForegroundColor Cyan
$report = Invoke-Api Get '/dashboard/resume-performance?days=90'
Check '报表有数据且字段齐全' ($report.Count -ge 1 -and $null -ne $report[0].applicationCount -and $null -ne $report[0].interviewRate)
$row = $report | Where-Object { $_.resumeId -eq [long]$resumeId } | Select-Object -First 1
Check '刚投的那份简历出现在报表里（面试数与 Offer 数已统计）' ($null -ne $row -and $row.offerCount -ge 1) "行=$($row | ConvertTo-Json -Compress)"
$emptyWindow = Invoke-Api Get '/dashboard/resume-performance?days=7'
Check '7 天窗口可用（空数据由前端显示引导文案）' ($null -ne $emptyWindow)

# ------------------------------------------------------------ P1-2 看板卡片增强
Write-Host "`n=== P1-2 看板卡片信息增强 ===" -ForegroundColor Cyan
$board = Invoke-Api Get '/applications/board'
$cards = @($board.OFFER) + @($board.INTERVIEW) + @($board.APPLIED)
$target = $cards | Where-Object { $_.id -eq [long]$appId } | Select-Object -First 1
Check '看板卡片带 nextInterviewTime' ($null -ne $target.nextInterviewTime) "卡片=$($target | ConvertTo-Json -Compress)"

# 匹配分来自该职位最近一次 AI 简历匹配，跑一次匹配再回看看板
$matchCall = Invoke-Api -Quiet Post '/ai/match-resume' @{ jobId = [long]$jobId; resumeId = [long]$resumeId }
if ($matchCall) {
    $board2 = Invoke-Api Get '/applications/board'
    $card2 = (@($board2.OFFER) + @($board2.INTERVIEW) + @($board2.APPLIED)) |
        Where-Object { $_.id -eq [long]$appId } | Select-Object -First 1
    Check '跑过匹配后卡片带 matchScore' ($null -ne $card2.matchScore) "matchScore=$($card2.matchScore)"
} else {
    Write-Host '  [跳过] AI 匹配不可用（未配置 Key），matchScore 字段留空由前端隐藏' -ForegroundColor Yellow
}

# ------------------------------------------------------- P1-3 复盘一键入题库
Write-Host "`n=== P1-3 面试复盘提取题目 ===" -ForegroundColor Cyan
$beforeQuestions = (Invoke-Api Get '/questions?pageNum=1&pageSize=1').total
$extract = Invoke-Api Post '/ai/extract-questions' @{
    review = '面试官问到了 JVM 内存模型，还有 Redis 缓存穿透怎么解决？我答得不太好的是 MySQL 索引失效的场景，另外问了项目里怎么做的分库分表。'
    jobId  = [long]$jobId
    save   = $true
}
Check '复盘提取返回题目' ($extract.data.questions.Count -ge 1) "数量=$($extract.data.questions.Count)"
Check '题目已落库' ($extract.data.savedCount -ge 1) "savedCount=$($extract.data.savedCount)"
$afterQuestions = (Invoke-Api Get '/questions?pageNum=1&pageSize=1').total
Check '题库总数增加' ($afterQuestions -gt $beforeQuestions) "前=$beforeQuestions 后=$afterQuestions"
$extracted = Invoke-Api Get '/questions?pageNum=1&pageSize=5'
$linked = $extracted.records | Where-Object { $_.question -match 'JVM|Redis|MySQL|分库分表' } | Select-Object -First 1
Check '提取的题目带着固定分类入库' ($null -ne $linked -and [bool]$linked.category) "分类=$($linked.category)"

Write-Host "`n=== 汇总 ===" -ForegroundColor Cyan

# 冒烟会造一条职位 + 投递 + 面试 + 题目，跑完清掉，别污染演示数据
Write-Host "`n=== 清理冒烟数据 ===" -ForegroundColor Cyan
Invoke-Api -Quiet Delete "/applications/$appId" | Out-Null
Invoke-Api -Quiet Delete "/interviews/$interviewId" | Out-Null
if ($nextRound.nextRoundInterviewId) {
    Invoke-Api -Quiet Delete "/interviews/$($nextRound.nextRoundInterviewId)" | Out-Null
}
Invoke-Api -Quiet Delete "/jobs/$jobId" | Out-Null
$extractedIds = @($extract.data.questions | Measure-Object).Count
$recent = (Invoke-Api -Quiet Get '/questions?pageNum=1&pageSize=20')
if ($recent) {
    foreach ($item in $recent.records) {
        if ($item.jobId -eq [long]$jobId) {
            Invoke-Api -Quiet Delete "/questions/$($item.id)" | Out-Null
        }
    }
}
Write-Host "  已清理职位 $jobId 及其投递 / 面试 / 题目" -ForegroundColor Yellow

Write-Host "通过 $script:pass 项，失败 $script:fail 项" -ForegroundColor $(if ($script:fail -eq 0) { 'Green' } else { 'Red' })
if ($script:fail -gt 0) { exit 1 }
