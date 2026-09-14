# ============================================================================
#  演示数据种子脚本
#
#  作用：注册/登录一个演示账号，然后批量创建公司、职位、简历、投递和面试记录，
#        让首页看板、投递看板、统计图和 AI 分析都有真实数据可看。
#
#  特点：可重复执行。已存在的公司 / 职位 / 简历会按名称复用，不会重复插入；
#        一个职位只能有一条投递记录，脚本会自动跳过。
#
#  用法（先把后端跑起来）：
#     powershell -ExecutionPolicy Bypass -File scripts/seed-demo.ps1
#     powershell -ExecutionPolicy Bypass -File scripts/seed-demo.ps1 -Reset    # 先清空演示数据再重建
#
#  参数：
#     -BaseUrl   后端地址，默认 http://localhost:8088/api
#     -Username  演示账号，默认 demo
#     -Password  演示账号密码，默认 123456
#     -Reset     先删除该账号下已有的业务数据，再重新生成
# ============================================================================
param(
    [string]$BaseUrl = 'http://localhost:8088/api',
    [string]$Username = 'demo',
    [string]$Password = '123456',
    [switch]$Reset
)

$ErrorActionPreference = 'Stop'
$headers = @{}

# ---------------------------------------------------------------------------
# 统一请求封装。
# 注意：PowerShell 5.1 的 Invoke-RestMethod 在响应头缺少 charset 时会按 Latin-1
# 解码，中文会变成乱码，所以这里改成手动按 UTF-8 解码原始字节流。
# ---------------------------------------------------------------------------
function Invoke-Api {
    param([string]$Method, [string]$Path, $Body)

    $params = @{
        Uri             = "$BaseUrl$Path"
        Method          = $Method
        Headers         = $headers
        UseBasicParsing = $true
    }
    if ($null -ne $Body) {
        $params.Body = [System.Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 8 -Compress))
        $params.ContentType = 'application/json; charset=utf-8'
    }

    try {
        $response = Invoke-WebRequest @params
    } catch {
        # HTTP 层面的错误（401/404/500）需要从异常里把响应体抠出来
        $httpResponse = $_.Exception.Response
        if (-not $httpResponse) { throw }
        $reader = New-Object System.IO.StreamReader($httpResponse.GetResponseStream(), [System.Text.Encoding]::UTF8)
        $text = $reader.ReadToEnd()
        $reader.Close()
        if (-not $text) { throw }
        return ($text | ConvertFrom-Json)
    }

    $text = [System.Text.Encoding]::UTF8.GetString($response.RawContentStream.ToArray())
    if (-not $text) { return $null }
    return ($text | ConvertFrom-Json)
}

# 取 data；业务失败（HTTP 200 但 code != 200）时打印原因并返回 $null
function Get-Data {
    param($Response, [switch]$Quiet)
    if ($null -eq $Response) { return $null }
    if ($Response.code -ne 200) {
        if (-not $Quiet) {
            Write-Host ("      跳过：{0}" -f $Response.message) -ForegroundColor DarkYellow
        }
        return $null
    }
    return $Response.data
}

function New-Jd {
    param([string]$Stack, [string]$Exp, [string]$Extra)
    return @"
岗位职责：
1. 负责核心业务系统的后端设计与开发，参与需求评审和技术方案设计
2. 负责线上服务的性能优化与稳定性治理，保障高并发场景下的系统可用性
3. 参与微服务架构演进，推动服务治理、中间件与研发效率建设

任职要求：
1. 本科及以上学历，$Exp 后端开发经验
2. 精通 Java，熟悉 JVM 原理、多线程与并发编程
3. 熟练使用 $Stack
4. $Extra
5. 有分布式系统、高并发项目经验者优先，具备良好的沟通能力和责任心
"@
}

# ---------------------------------------------------------------- 1. 登录
Write-Host '== 1/7 登录演示账号 ==' -ForegroundColor Cyan
$login = Get-Data (Invoke-Api -Method Post -Path '/auth/login' -Body @{ username = $Username; password = $Password }) -Quiet
if (-not $login) {
    $login = Get-Data (Invoke-Api -Method Post -Path '/auth/register' -Body @{ username = $Username; password = $Password; nickname = '演示用户' })
}
if (-not $login) { throw '登录失败，请确认后端已启动' }
$headers = @{ Authorization = "Bearer $($login.token)" }
Write-Host ("   当前用户：{0}（id={1}）" -f $login.user.nickname, $login.user.id)

# ------------------------------------------------------- 2. 可选：清理旧数据
if ($Reset) {
    Write-Host '== 2/7 清理旧的演示数据 ==' -ForegroundColor Cyan
    function Remove-All {
        param([string]$Label, [string]$ItemPath, [string]$ListPath)
        $data = Get-Data (Invoke-Api -Method Get -Path $ListPath)
        if ($null -eq $data) { return }
        # 分页接口返回 {total,records:[...]}，/companies/all 这类接口直接返回数组
        if ($data -is [System.Array]) {
            $records = $data
        } elseif ($data.PSObject.Properties['records']) {
            $records = @($data.records)
        } else {
            $records = @($data)
        }
        $count = 0
        foreach ($r in $records) {
            $res = Invoke-Api -Method Delete -Path "$ItemPath/$($r.id)"
            if ($res -and $res.code -eq 200) { $count++ }
        }
        Write-Host ("   已删除 {0} 条{1}" -f $count, $Label) -ForegroundColor DarkGray
    }
    Remove-All -Label '面试记录' -ItemPath '/interviews'   -ListPath '/interviews?page=1&pageSize=500'
    Remove-All -Label '投递记录' -ItemPath '/applications' -ListPath '/applications?page=1&pageSize=500'
    Remove-All -Label '面试题'   -ItemPath '/questions'    -ListPath '/questions?page=1&pageSize=500'
    Remove-All -Label '职位'     -ItemPath '/jobs'         -ListPath '/jobs?page=1&pageSize=500'
    Remove-All -Label '公司'     -ItemPath '/companies'    -ListPath '/companies/all'
    Remove-All -Label '简历'     -ItemPath '/resumes'      -ListPath '/resumes/all'
} else {
    Write-Host '== 2/7 清理旧的演示数据（跳过，加 -Reset 可清空重建）==' -ForegroundColor DarkGray
}

# ---------------------------------------------------------------- 3. 公司
Write-Host '== 3/7 公司 ==' -ForegroundColor Cyan
$companySeeds = @(
    @{ name = '字节跳动';   industry = '互联网';   scale = '10000人以上';  city = '北京'; status = 'CONTACTED'; remark = '面试流程规范，重视计算机基础' },
    @{ name = '阿里巴巴';   industry = '电商';     scale = '10000人以上';  city = '杭州'; status = 'TARGET';    remark = '目标是交易 / 中间件团队' },
    @{ name = '美团';       industry = '本地生活'; scale = '10000人以上';  city = '北京'; status = 'TARGET';    remark = '' },
    @{ name = '小红书';     industry = '社区';     scale = '1000-9999人';  city = '上海'; status = 'CONTACTED'; remark = '技术栈较新，面试偏工程实践' },
    @{ name = '腾讯';       industry = '互联网';   scale = '10000人以上';  city = '深圳'; status = 'TARGET';    remark = '后台开发岗位机会较多' },
    @{ name = '某创业公司'; industry = 'SaaS';     scale = '20-99人';      city = '远程'; status = 'CLOSED';    remark = '业务方向调整，暂缓推进' }
)

$knownCompanies = @{}
foreach ($c in (Get-Data (Invoke-Api -Method Get -Path '/companies/all'))) { $knownCompanies[$c.name] = $c.id }

$companyIds = @{}
foreach ($seed in $companySeeds) {
    if ($knownCompanies.ContainsKey($seed.name)) {
        $companyIds[$seed.name] = $knownCompanies[$seed.name]
        Write-Host ("   = 复用 {0}" -f $seed.name) -ForegroundColor DarkGray
    } else {
        $id = Get-Data (Invoke-Api -Method Post -Path '/companies' -Body $seed)
        if ($id) {
            $companyIds[$seed.name] = $id
            Write-Host ("   + 新建 {0}" -f $seed.name)
        }
    }
}

# ---------------------------------------------------------------- 4. 职位
Write-Host '== 4/7 职位 ==' -ForegroundColor Cyan
$jobSpecs = @(
    @{ company = '字节跳动';   jobName = 'Java 后端开发工程师';           min = 25; max = 45; location = '北京·海淀区'; status = 'OPEN';   url = 'https://jobs.bytedance.com';  stack = 'Spring Boot、MyBatis、Spring Cloud';                   exp = '3-5年'; extra = '熟悉 MySQL 索引优化与慢 SQL 排查，熟悉 Redis 缓存与分布式锁' },
    @{ company = '字节跳动';   jobName = '后端开发工程师（基础架构）';     min = 30; max = 60; location = '北京·海淀区'; status = 'OPEN';   url = '';                            stack = 'Java、Spring Boot、Redis、Kafka、Docker、Kubernetes';   exp = '3-5年'; extra = '熟悉微服务治理与云原生技术栈，有中间件开发经验优先' },
    @{ company = '字节跳动';   jobName = '电商服务端开发工程师';           min = 28; max = 52; location = '北京·海淀区'; status = 'OPEN';   url = '';                            stack = 'Spring Boot、MyBatis、Redis、Kafka';                    exp = '3-5年'; extra = '熟悉订单、库存、支付等交易链路，有高并发场景经验' },
    @{ company = '字节跳动';   jobName = 'Java 开发工程师（增长方向）';    min = 26; max = 48; location = '上海·杨浦区'; status = 'OPEN';   url = '';                            stack = 'Spring Boot、MyBatis、MySQL、Redis';                    exp = '2-4年'; extra = '对数据敏感，能独立完成 A/B 实验相关的服务端开发' },
    @{ company = '阿里巴巴';   jobName = '高级 Java 工程师';               min = 35; max = 60; location = '杭州·余杭区'; status = 'OPEN';   url = 'https://talent.alibaba.com';  stack = 'Spring Boot、Spring Cloud、MyBatis-Plus';               exp = '5-8年'; extra = '熟悉 Kafka 消息队列与分布式事务，有分库分表实战经验' },
    @{ company = '阿里巴巴';   jobName = '中间件研发工程师';               min = 38; max = 65; location = '杭州·余杭区'; status = 'OPEN';   url = '';                            stack = 'Java、Netty、RocketMQ、Redis';                          exp = '3-5年'; extra = '熟悉 Netty 网络编程与消息中间件原理，读过高性能框架源码' },
    @{ company = '阿里巴巴';   jobName = '交易平台后端开发';               min = 30; max = 55; location = '杭州·滨江区'; status = 'OPEN';   url = '';                            stack = 'Spring Boot、MyBatis、Tair、Kafka';                     exp = '3-5年'; extra = '熟悉分布式事务与一致性方案，有资金类业务经验优先' },
    @{ company = '阿里巴巴';   jobName = 'Java 开发工程师（履约方向）';    min = 28; max = 50; location = '杭州·余杭区'; status = 'OPEN';   url = '';                            stack = 'Spring Boot、MyBatis、MySQL、Redis';                    exp = '3-5年'; extra = '熟悉状态机建模与异步任务调度，有履约 / 物流业务经验优先' },
    @{ company = '美团';       jobName = '后端开发工程师（交易方向）';     min = 28; max = 50; location = '北京·朝阳区'; status = 'OPEN';   url = '';                            stack = 'Spring Boot、MyBatis、Redis、Kafka';                    exp = '3-5年'; extra = '熟悉高并发交易系统设计，了解分布式系统与消息队列' },
    @{ company = '美团';       jobName = 'Java 开发工程师（到店）';        min = 26; max = 45; location = '北京·朝阳区'; status = 'OPEN';   url = '';                            stack = 'Spring Boot、MyBatis、MySQL、Redis';                    exp = '2-4年'; extra = '熟悉优惠券、营销活动等业务建模，能快速理解业务' },
    @{ company = '美团';       jobName = '配送调度后端工程师';             min = 27; max = 48; location = '北京·望京';   status = 'OPEN';   url = '';                            stack = 'Java、Spring Boot、Kafka、Redis';                       exp = '3-5年'; extra = '对运筹优化、路径规划有兴趣，熟悉大数据量下的性能调优' },
    @{ company = '小红书';     jobName = 'Java 开发工程师';                min = 30; max = 55; location = '上海·徐汇区'; status = 'OPEN';   url = '';                            stack = 'Spring Boot、MyBatis、Spring Cloud';                    exp = '3-5年'; extra = '熟悉 Elasticsearch 与推荐相关业务，具备性能优化经验' },
    @{ company = '小红书';     jobName = '社区后端开发工程师';             min = 28; max = 52; location = '上海·徐汇区'; status = 'OPEN';   url = '';                            stack = 'Spring Boot、MyBatis、Redis、Kafka';                    exp = '3-5年'; extra = '熟悉 Feed 流、评论、点赞等高并发读多写少场景的设计' },
    @{ company = '小红书';     jobName = '推荐工程后端工程师';             min = 32; max = 58; location = '上海·徐汇区'; status = 'OPEN';   url = '';                            stack = 'Java、Spring Boot、Redis、Flink';                       exp = '3-5年'; extra = '了解推荐系统链路，熟悉特征工程与实时计算优先' },
    @{ company = '腾讯';       jobName = '后台开发工程师';                 min = 26; max = 50; location = '深圳·南山区'; status = 'OPEN';   url = 'https://join.qq.com';         stack = 'Spring Boot、MyBatis、MySQL、Redis';                    exp = '3-5年'; extra = '熟悉 C++ 或 Java 任一技术栈，基础扎实，有线上问题排查经验' },
    @{ company = '腾讯';       jobName = '云原生后端开发工程师';           min = 30; max = 55; location = '深圳·南山区'; status = 'OPEN';   url = '';                            stack = 'Java、Spring Boot、Docker、Kubernetes、Kafka';          exp = '3-5年'; extra = '熟悉容器编排与服务可观测性建设，了解 Service Mesh' },
    @{ company = '腾讯';       jobName = 'Java 开发工程师（支付方向）';    min = 28; max = 52; location = '深圳·南山区'; status = 'OPEN';   url = '';                            stack = 'Spring Boot、MyBatis、Redis、MySQL';                    exp = '3-5年'; extra = '熟悉资金安全与幂等设计，有支付 / 账务系统经验优先' },
    @{ company = '某创业公司'; jobName = '全栈开发工程师';                 min = 18; max = 30; location = '远程';        status = 'OPEN';   url = '';                            stack = 'Spring Boot、Vue3、MySQL、Redis';                       exp = '2-4年'; extra = '能独立负责从需求到上线的完整链路，接受远程办公' },
    @{ company = '某创业公司'; jobName = 'Java 后端开发（远程）';          min = 20; max = 35; location = '远程';        status = 'OPEN';   url = '';                            stack = 'Spring Boot、MyBatis-Plus、MySQL';                      exp = '2-4年'; extra = '业务节奏快，需要有较强的问题定位能力和自驱力' },
    @{ company = '某创业公司'; jobName = '后端开发工程师（AI 应用）';      min = 22; max = 38; location = '远程';        status = 'CLOSED'; url = '';                            stack = 'Spring Boot、MySQL、Redis、大模型 API';                 exp = '2-4年'; extra = '有大模型应用落地经验优先，了解 Prompt 工程与 RAG 检索增强' }
)

$knownJobs = @{}
foreach ($j in (Get-Data (Invoke-Api -Method Get -Path '/jobs?page=1&pageSize=500')).records) { $knownJobs[$j.jobName] = $j.id }

$jobIds = @()
$createdJobs = 0
foreach ($spec in $jobSpecs) {
    $companyId = $companyIds[$spec.company]
    if (-not $companyId) { continue }
    if ($knownJobs.ContainsKey($spec.jobName)) {
        $jobIds += $knownJobs[$spec.jobName]
        continue
    }
    $body = @{
        companyId      = $companyId
        jobName        = $spec.jobName
        jobDescription = (New-Jd -Stack $spec.stack -Exp $spec.exp -Extra $spec.extra)
        salaryMin      = $spec.min
        salaryMax      = $spec.max
        location       = $spec.location
        status         = $spec.status
    }
    if ($spec.url) { $body.jobUrl = $spec.url }
    $id = Get-Data (Invoke-Api -Method Post -Path '/jobs' -Body $body)
    if ($id) { $jobIds += $id; $createdJobs++ }
}
Write-Host ("   职位总数 {0}（本次新建 {1}）" -f $jobIds.Count, $createdJobs)

# ---------------------------------------------------------------- 5. 简历
Write-Host '== 5/7 简历 ==' -ForegroundColor Cyan
$resumeSeeds = @(
    @{
        title = 'Java后端-社招版'; name = '张三'; phone = '13800000000'; email = 'zhangsan@example.com'
        education = '本科'; workYears = 3; isDefault = 1
        skills = 'Java,Spring Boot,MyBatis,MySQL,Redis,Linux,Git,Maven'
        summary = '3 年 Java 后端开发经验，主要负责电商交易系统的开发与性能优化。'
        content = @"
项目经历一：交易系统重构（2024.03 - 2025.01）
负责订单与支付模块的重构，引入 Redis 缓存热点商品与库存，接口平均耗时从 800ms 降到 120ms，
单机 QPS 从 800 提升到 3000；使用本地缓存 + Redis 二级缓存解决缓存击穿问题。

项目经历二：对账服务（2023.06 - 2024.02）
使用 Spring Boot + MyBatis 搭建对账服务，通过 Kafka 消费交易流水，日均处理 200 万笔，
引入分片 + 批量提交后对账任务耗时从 40 分钟降到 6 分钟。

技能：Java 基础扎实，熟悉 JVM 内存模型与多线程；熟悉 Spring Boot、MyBatis、MySQL 索引优化；
熟悉 Redis 常用数据结构与缓存设计；了解 Kafka 消息队列；熟练使用 Linux、Git、Maven。
"@
    },
    @{
        title = 'Java后端-大厂冲刺版'; name = '张三'; phone = '13800000000'; email = 'zhangsan@example.com'
        education = '本科'; workYears = 3; isDefault = 0
        skills = 'Java,JVM,Spring Cloud,MySQL,Redis,Kafka,分布式系统,微服务,高并发'
        summary = '3 年后端经验，深耕高并发与分布式方向，主导过交易链路性能优化。'
        content = @"
核心优势：
1. 主导交易链路性能优化，通过缓存分层、异步化与 SQL 优化，把核心接口 P99 从 1.2s 降到 180ms；
2. 参与微服务拆分，基于 Spring Cloud 完成订单服务与库存服务解耦，线上故障率下降 60%；
3. 熟悉 JVM 调优，通过调整 G1 参数与排查内存泄漏解决过多次线上 Full GC 问题；
4. 熟悉 Kafka、Redis、分库分表等中间件与架构方案，有高并发大促保障经验。
"@
    }
)

$knownResumes = @{}
foreach ($r in (Get-Data (Invoke-Api -Method Get -Path '/resumes/all'))) { $knownResumes[$r.title] = $r.id }

$resumeIds = @()
foreach ($seed in $resumeSeeds) {
    if ($knownResumes.ContainsKey($seed.title)) {
        $resumeIds += $knownResumes[$seed.title]
        Write-Host ("   = 复用 {0}" -f $seed.title) -ForegroundColor DarkGray
    } else {
        $id = Get-Data (Invoke-Api -Method Post -Path '/resumes' -Body $seed)
        if ($id) { $resumeIds += $id; Write-Host ("   + 新建 {0}" -f $seed.title) }
    }
}
if ($resumeIds.Count -eq 0) { throw '没有可用简历，无法创建投递记录' }
if ($jobIds.Count -eq 0) { throw '没有可用职位，无法创建投递记录' }

# ------------------------------------------------------- 6. 投递记录（近 30 天）
Write-Host '== 6/7 投递记录 ==' -ForegroundColor Cyan
# 数组下标越大 = 投递时间越近 = 流程阶段越早，看板和趋势图看起来才自然
$statusPlan = @(
    'REJECTED', 'OFFER', 'REJECTED', 'INTERVIEW', 'CLOSED',
    'REJECTED', 'OFFER', 'WRITTEN_TEST', 'INTERVIEW', 'REJECTED',
    'APPLIED', 'WRITTEN_TEST', 'INTERVIEW', 'APPLIED', 'APPLIED',
    'APPLIED', 'WISHLIST', 'APPLIED', 'WISHLIST', 'APPLIED'
)
$sources  = @('Boss直聘', '拉勾', '猎聘', '内推', '官网')
$remarks  = @('', '已联系 HR，等回复', '内推人帮忙跟进中', '', 'JD 和简历匹配度一般', '', '准备重点补一下中间件')

# 一个职位只能有一条投递记录，这里按 jobId 去重
$existingJobIds = @{}
foreach ($a in (Get-Data (Invoke-Api -Method Get -Path '/applications?page=1&pageSize=500')).records) {
    $existingJobIds[$a.jobId] = $true
}

$createdApps = 0
$skippedApps = 0
for ($i = 0; $i -lt $statusPlan.Count; $i++) {
    $jobId = $jobIds[$i % $jobIds.Count]
    if ($existingJobIds.ContainsKey($jobId)) { $skippedApps++; continue }

    $status   = $statusPlan[$i]
    $daysAgo  = [Math]::Max(0, 29 - [int]($i * 29 / $statusPlan.Count))
    $applyTime = (Get-Date).AddDays(-$daysAgo).Date.AddHours(9 + ($i % 9)).AddMinutes(($i * 13) % 60)

    $body = @{
        jobId             = $jobId
        resumeId          = $resumeIds[$i % $resumeIds.Count]
        applicationStatus = $status
        source            = $sources[$i % $sources.Count]
        remark            = $remarks[$i % $remarks.Count]
    }
    if ($status -ne 'WISHLIST') {
        $body.applyTime = $applyTime.ToString('yyyy-MM-dd HH:mm:ss')
    }

    $id = Get-Data (Invoke-Api -Method Post -Path '/applications' -Body $body) -Quiet
    if ($id) { $existingJobIds[$jobId] = $true; $createdApps++ } else { $skippedApps++ }
}
Write-Host ("   新建 {0} 条，跳过 {1} 条（已存在的职位）" -f $createdApps, $skippedApps)

# ---------------------------------------------------------------- 7. 面试
Write-Host '== 7/7 面试记录 ==' -ForegroundColor Cyan
$allApplications = (Get-Data (Invoke-Api -Method Get -Path '/applications?page=1&pageSize=500')).records
$byStatus = @{}
foreach ($a in $allApplications) {
    if (-not $byStatus.ContainsKey($a.applicationStatus)) { $byStatus[$a.applicationStatus] = @() }
    $byStatus[$a.applicationStatus] += $a
}

$existingInterviews = @{}
foreach ($iv in (Get-Data (Invoke-Api -Method Get -Path '/interviews?page=1&pageSize=500')).records) {
    $existingInterviews["$($iv.applicationId)|$($iv.roundName)"] = $true
}

$interviewSeeds = @(
    @{ status = 'INTERVIEW'; roundName = '技术一面'; type = 'VIDEO';  days =  2; interviewer = '王工';   result = 'PENDING'; review = '' },
    @{ status = 'INTERVIEW'; roundName = '技术二面'; type = 'ONSITE'; days =  5; interviewer = '李工';   result = 'PENDING'; review = '' },
    @{ status = 'INTERVIEW'; roundName = 'HR 面';    type = 'PHONE';  days =  8; interviewer = '张女士'; result = 'PENDING'; review = '' },
    @{ status = 'OFFER';     roundName = '技术一面'; type = 'VIDEO';  days = -9; interviewer = '赵工';   result = 'PASS';    review = '基础扎实，项目细节讲得比较清楚，注意控制回答的节奏' },
    @{ status = 'OFFER';     roundName = '技术二面'; type = 'VIDEO';  days = -6; interviewer = '孙工';   result = 'PASS';    review = '对分布式事务的理解不错，可以再补一些落地案例' },
    @{ status = 'REJECTED';  roundName = '技术一面'; type = 'VIDEO';  days = -4; interviewer = '钱工';   result = 'FAIL';    review = 'JVM 调优和分布式事务这块答得不够深入，需要重点补一下' }
)

$usedApplications = @{}
$createdInterviews = 0
foreach ($seed in $interviewSeeds) {
    $pool = @($byStatus[$seed.status])
    $target = $null
    foreach ($app in $pool) {
        if (-not $usedApplications.ContainsKey($app.id)) { $target = $app; break }
    }
    if (-not $target -and $pool.Count -gt 0) { $target = $pool[0] }
    if (-not $target) {
        Write-Host ("   跳过「{0}」：没有 {1} 状态的投递记录" -f $seed.roundName, $seed.status) -ForegroundColor DarkYellow
        continue
    }
    $usedApplications[$target.id] = $true

    if ($existingInterviews.ContainsKey("$($target.id)|$($seed.roundName)")) {
        Write-Host ("   = 已存在「{0}」" -f $seed.roundName) -ForegroundColor DarkGray
        continue
    }

    $body = @{
        applicationId = $target.id
        roundName     = $seed.roundName
        interviewType = $seed.type
        interviewTime = (Get-Date).AddDays($seed.days).Date.AddHours(14).ToString('yyyy-MM-dd HH:mm:ss')
        interviewer   = $seed.interviewer
        result        = $seed.result
    }
    if ($seed.review) { $body.review = $seed.review }
    if ($seed.type -eq 'VIDEO') { $body.meetingUrl = 'https://meeting.example.com/' + [Guid]::NewGuid().ToString('N').Substring(0, 8) }

    $id = Get-Data (Invoke-Api -Method Post -Path '/interviews' -Body $body)
    if ($id) {
        $createdInterviews++
        Write-Host ("   + {0}（{1}，{2}）" -f $seed.roundName, $seed.result, $target.companyName)
    }
}
Write-Host ("   新建 {0} 场面试" -f $createdInterviews)

# ---------------------------------------------------------------- 汇总
Write-Host ''
$dashboard = Get-Data (Invoke-Api -Method Get -Path '/dashboard')
if ($dashboard) {
    Write-Host '当前数据概览：' -ForegroundColor Cyan
    Write-Host ("   公司 {0} 家 / 职位 {1} 个 / 简历 {2} 份" -f $companyIds.Count, $jobIds.Count, $resumeIds.Count)
    Write-Host ("   投递 {0} 条 / 面试 {1} 场 / Offer {2} 个" -f $dashboard.totalApplications, $dashboard.interviewCount, $dashboard.offerCount)
}
Write-Host ''
Write-Host '演示数据初始化完成 ✅' -ForegroundColor Green
Write-Host '后端接口文档：http://localhost:8088/api/doc.html' -ForegroundColor Green
Write-Host '前端页面：    http://localhost:5173' -ForegroundColor Green