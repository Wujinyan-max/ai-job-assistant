package com.jobassistant.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 本地模拟 AI。
 * <p>没有配置 API Key 时用它兜底：用技术关键词字典 + 正则做规则抽取，
 * 输出结构与真实模型完全一致，保证「下载即能跑通全流程」。
 * 它只是演示与降级方案，真实场景请配置 ai.api-key。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockAiEngine {

    private static final List<String> TECH_KEYWORDS = List.of(
            "JavaScript", "TypeScript", "Spring Boot", "Spring Cloud", "Spring MVC", "MyBatis-Plus",
            "MyBatis", "Hibernate", "Elasticsearch", "RabbitMQ", "RocketMQ", "Kubernetes",
            "ShardingSphere", "Prometheus", "Grafana", "SkyWalking", "ZooKeeper", "Nginx",
            "MongoDB", "PostgreSQL", "Docker", "Kafka", "Redis", "MySQL", "Oracle", "Jenkins",
            "Maven", "Git", "Linux", "Dubbo", "Netty", "Sentinel", "Nacos", "Flink", "Spark",
            "Hadoop", "Hive", "Java", "JVM", "Python", "Golang", "Vue", "React", "Node.js",
            "HTML", "CSS", "RESTful", "HTTP", "TCP", "CI/CD", "DevOps", "SQL", "SSM");

    private static final List<String> CHINESE_KEYWORDS = List.of(
            "分布式系统", "分布式事务", "分布式中间件", "高并发", "高可用", "高性能",
            "微服务架构", "微服务", "消息队列", "分库分表", "读写分离", "缓存",
            "性能优化", "多线程", "并发编程", "JVM调优", "设计模式", "数据结构",
            "算法", "单元测试", "领域驱动", "中间件", "容器化", "云原生",
            "团队协作", "沟通能力", "业务理解", "责任心", "英文读写");

    private static final List<String> TECH_BY_LENGTH = sortByLengthDesc(TECH_KEYWORDS);
    private static final List<String> CN_BY_LENGTH = sortByLengthDesc(CHINESE_KEYWORDS);

    private static final Pattern EXPERIENCE_RANGE = Pattern.compile("(\\d+)\\s*[-~至到]\\s*(\\d+)\\s*年");
    private static final Pattern EXPERIENCE_ABOVE = Pattern.compile("(\\d+)\\s*年(?:以上|及以上|工作经验)");
    private static final Pattern RESPONSIBILITY_LINE = Pattern.compile("^\\s*(?:\\d+[.、)]|[-*·])?\\s*(负责|参与|主导|设计|搭建|维护|推动|承担).{6,60}$");

    private final ObjectMapper objectMapper;

    public String reply(AiRequest request) {
        Map<String, Object> result = switch (request.task()) {
            case JD_ANALYZE -> analyzeJd(text(request, "jd"));
            case RESUME_MATCH -> matchResume(text(request, "jd"), text(request, "resume"));
            case INTERVIEW_QUESTION -> generateQuestions(request);
        };
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("本地模拟 AI 输出 JSON 失败", e);
        }
    }

    // ------------------------------------------------------------------ JD 分析

    private Map<String, Object> analyzeJd(String jd) {
        List<String> skills = extract(jd, TECH_BY_LENGTH).stream().limit(15).toList();
        List<String> keywords = extract(jd, CN_BY_LENGTH).stream().limit(10).toList();
        String experience = extractExperience(jd);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("skills", skills);
        result.put("keywords", keywords);
        result.put("experience", experience);
        result.put("education", extractEducation(jd));
        result.put("seniority", guessSeniority(experience));
        result.put("responsibilities", extractResponsibilities(jd));
        result.put("summary", buildJdSummary(skills, keywords, experience));
        return result;
    }

    private List<String> extractResponsibilities(String jd) {
        List<String> result = new ArrayList<>();
        for (String line : jd.split("\\R")) {
            String trimmed = line.trim();
            if (RESPONSIBILITY_LINE.matcher(trimmed).matches()) {
                String cleaned = trimmed.replaceFirst("^\\s*(?:\\d+[.、)]|[-*·])\\s*", "");
                result.add(cleaned.length() > 40 ? cleaned.substring(0, 40) : cleaned);
            }
            if (result.size() >= 5) {
                break;
            }
        }
        return result;
    }

    private String buildJdSummary(List<String> skills, List<String> keywords, String experience) {
        StringBuilder sb = new StringBuilder("该岗位主要考察 ");
        sb.append(skills.isEmpty() ? "通用后端开发能力" : String.join("、", skills.subList(0, Math.min(5, skills.size()))));
        if (!keywords.isEmpty()) {
            sb.append("，关注 ").append(String.join("、", keywords.subList(0, Math.min(3, keywords.size()))));
        }
        sb.append("，经验要求 ").append(experience).append("。");
        return sb.toString();
    }

    // -------------------------------------------------------------- 简历匹配分析

    private Map<String, Object> matchResume(String jd, String resume) {
        Set<String> required = new LinkedHashSet<>(extract(jd, TECH_BY_LENGTH));
        required.addAll(extract(jd, CN_BY_LENGTH));

        Set<String> owned = new LinkedHashSet<>(extract(resume, TECH_BY_LENGTH));
        owned.addAll(extract(resume, CN_BY_LENGTH));

        List<String> matched = required.stream().filter(owned::contains).toList();
        List<String> missing = required.stream().filter(s -> !owned.contains(s)).toList();

        int score = required.isEmpty() ? 60 : (int) Math.round(100.0 * matched.size() / required.size());
        score = Math.max(0, Math.min(100, score));

        List<String> strengths = new ArrayList<>();
        if (!matched.isEmpty()) {
            strengths.add("已具备岗位要求的 " + String.join("、", matched.subList(0, Math.min(5, matched.size()))) + " 等技能");
        }
        if (containsAny(resume, "负责", "主导", "owner", "Owner")) {
            strengths.add("简历中有明确的职责与主导经验描述");
        }
        if (containsAny(resume, "%", "QPS", "qps", "万", "倍", "ms")) {
            strengths.add("项目成果有量化数据支撑");
        }
        if (strengths.isEmpty()) {
            strengths.add("简历结构可以支撑基本信息筛查");
        }

        List<String> suggestions = new ArrayList<>();
        missing.stream().limit(3).forEach(skill ->
                suggestions.add("补充「" + skill + "」相关的项目经验，写清你承担的具体职责和产出"));
        suggestions.add("用量化结果描述项目，例如 QPS 从 X 提升到 Y、接口耗时从 X ms 降到 Y ms");
        suggestions.add("在个人简介首屏点出与该岗位最匹配的 2-3 个关键词，方便 HR 快速命中");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("score", score);
        result.put("matchedSkills", matched);
        result.put("missingSkills", missing);
        result.put("strengths", strengths);
        result.put("suggestions", suggestions);
        result.put("comment", "整体匹配度 " + score + "%，属于「" + levelText(score)
                + "」水平。本轮由本地模拟引擎基于关键词规则计算，配置 AI_API_KEY 后可以得到更准确的语义评估。");
        return result;
    }

    private String levelText(int score) {
        if (score >= 85) {
            return "高度匹配，建议优先投递";
        }
        if (score >= 70) {
            return "比较匹配，简历稍作调整即可";
        }
        if (score >= 50) {
            return "基本匹配，需要补强缺失技能";
        }
        return "匹配度偏低，建议先补齐核心技能";
    }

    // ---------------------------------------------------------------- 面试题生成

    @SuppressWarnings("unchecked")
    private Map<String, Object> generateQuestions(AiRequest request) {
        List<String> categories = request.inputs().get("categories") instanceof List<?> list && !list.isEmpty()
                ? (List<String>) list
                : suggestCategories(text(request, "jd"));
        int count = request.inputs().get("count") instanceof Integer c ? Math.max(1, Math.min(5, c)) : 3;
        String difficulty = request.inputs().get("difficulty") instanceof String d ? d : "MEDIUM";

        List<Map<String, Object>> questions = new ArrayList<>();
        for (String category : categories) {
            List<Question> bank = QUESTION_BANK.getOrDefault(category, QUESTION_BANK.get("项目"));
            for (int i = 0; i < Math.min(count, bank.size()); i++) {
                Question q = bank.get(i);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("category", category);
                item.put("question", q.question());
                item.put("difficulty", difficulty);
                item.put("answer", q.answer());
                questions.add(item);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("questions", questions);
        return result;
    }

    private List<String> suggestCategories(String jd) {
        Set<String> skills = new LinkedHashSet<>(extract(jd, TECH_BY_LENGTH));
        List<String> categories = new ArrayList<>();
        if (skills.contains("Java") || skills.contains("JVM")) {
            categories.add("Java基础");
        }
        if (skills.contains("Spring Boot") || skills.contains("Spring Cloud") || skills.contains("Spring MVC")) {
            categories.add("Spring Boot");
        }
        if (skills.contains("MySQL") || skills.contains("PostgreSQL") || skills.contains("Oracle")) {
            categories.add("MySQL");
        }
        if (skills.contains("Redis")) {
            categories.add("Redis");
        }
        categories.add("项目");
        categories.add("HR");
        return categories.stream().limit(5).toList();
    }

    private record Question(String question, String answer) {
    }

    private static final Map<String, List<Question>> QUESTION_BANK = buildQuestionBank();

    private static Map<String, List<Question>> buildQuestionBank() {
        Map<String, List<Question>> bank = new LinkedHashMap<>();
        bank.put("Java基础", List.of(
                new Question("HashMap 的底层结构是什么？扩容机制是怎样的？",
                        "JDK8 起是数组 + 链表 + 红黑树。默认容量 16，负载因子 0.75，超过阈值按 2 倍扩容；链表长度 ≥ 8 且数组长度 ≥ 64 时转红黑树，退化阈值是 6。扩容时会重新分布元素，JDK8 用高低位链表避免了重新计算 hash。"),
                new Question("ConcurrentHashMap 是怎么保证线程安全的？",
                        "JDK7 用分段锁 Segment；JDK8 改为 CAS + synchronized 锁单个桶头节点，锁粒度更细。size 用 baseCount + CounterCell 分散计数，读操作基本无锁。"),
                new Question("JVM 的运行时内存区域怎么划分？",
                        "线程私有：程序计数器、虚拟机栈、本地方法栈；线程共享：堆、方法区（元空间）。堆分新生代（Eden + 两个 Survivor）和老年代，默认比例 8:1:1。"),
                new Question("线程池的核心参数有哪些？任务提交后怎么执行？",
                        "corePoolSize、maximumPoolSize、keepAliveTime、workQueue、threadFactory、handler。流程：核心线程未满则新建核心线程，否则入队，队列满则建非核心线程，仍满则触发拒绝策略。"),
                new Question("synchronized 和 ReentrantLock 有什么区别？",
                        "都是可重入的独占锁。synchronized 是 JVM 关键字，自动释放，偏向锁/轻量级锁/重量级锁逐级升级；ReentrantLock 是 API，支持公平锁、可中断、超时获取和多个 Condition。")));
        bank.put("Spring Boot", List.of(
                new Question("Spring Boot 自动配置的原理是什么？",
                        "@SpringBootApplication 里的 @EnableAutoConfiguration 通过 @Import(AutoConfigurationImportSelector) 读取 META-INF/spring.factories（Boot 2.7+ 为 AutoConfiguration.imports）里的配置类，再用 @Conditional 系列注解按需装配。"),
                new Question("Spring Bean 的生命周期是怎样的？",
                        "实例化 → 属性填充 → Aware 回调 → BeanPostProcessor 前置 → @PostConstruct / InitializingBean → BeanPostProcessor 后置（AOP 代理在这里生成）→ 使用 → @PreDestroy / DisposableBean 销毁。"),
                new Question("Spring 事务在什么情况下会失效？",
                        "方法不是 public、同类内部调用（this 调用绕过代理）、异常被 catch 掉、抛出的是检查异常且未配置 rollbackFor、多线程调用、类没有被 Spring 管理、数据库引擎不支持事务。"),
                new Question("Spring MVC 处理一个请求的完整流程？",
                        "DispatcherServlet 接收请求 → HandlerMapping 找到 Handler 和拦截器 → HandlerAdapter 执行 → 参数解析与绑定 → 调用 Controller → 返回 ModelAndView → ViewResolver 解析视图并渲染，Rest 场景由 HttpMessageConverter 直接写回 JSON。"),
                new Question("@Transactional 的传播行为有哪些？举个 REQUIRES_NEW 的场景。",
                        "REQUIRED、REQUIRES_NEW、SUPPORTS、NOT_SUPPORTED、MANDATORY、NEVER、NESTED。REQUIRES_NEW 适合「无论主流程是否回滚都要留下记录」的场景，比如操作日志、审计流水。")));
        bank.put("MySQL", List.of(
                new Question("InnoDB 的索引为什么用 B+ 树？",
                        "B+ 树非叶子节点只存索引不存数据，单页能放更多键值，树更矮、IO 更少；叶子节点用双向链表连接，天然支持范围查询和排序，比 B 树和哈希索引更均衡。"),
                new Question("哪些情况会导致索引失效？",
                        "对索引列做函数或运算、隐式类型转换、以 % 开头的 like、联合索引不满足最左前缀、使用 != 或 not in、or 连接的条件有列没建索引、order by 与 where 索引顺序不一致。"),
                new Question("MySQL 的事务隔离级别有哪些？默认是哪个？",
                        "读未提交、读已提交、可重复读、串行化。MySQL 默认是可重复读，通过 MVCC + 间隙锁基本解决了幻读问题。"),
                new Question("MVCC 是怎么实现的？",
                        "每行有隐藏的 trx_id 和 roll_pointer，配合 undo log 形成版本链；ReadView 记录活跃事务列表，通过可见性判断决定读哪个版本，从而实现快照读不加锁。"),
                new Question("一条慢 SQL 你会怎么排查和优化？",
                        "先用慢查询日志定位，explain 看 type、key、rows、Extra；再针对性优化：加合适的联合索引、避免 select *、减少回表、改写子查询为 join、必要时做分页优化或归档历史数据。" )));
        bank.put("Redis", List.of(
                new Question("Redis 的常用数据结构及适用场景？",
                        "String 做缓存和计数器，Hash 存对象，List 做消息队列，Set 做去重与共同好友，ZSet 做排行榜和延时队列，另外还有 Bitmap、HyperLogLog、GEO。"),
                new Question("缓存穿透、击穿、雪崩分别是什么？怎么解决？",
                        "穿透是查不存在的数据，用布隆过滤器或缓存空值；击穿是热点 key 过期瞬间大量请求打到 DB，用互斥锁或逻辑过期；雪崩是大量 key 同时过期或 Redis 宕机，用过期时间加随机值、多级缓存和集群高可用。"),
                new Question("Redis 的持久化机制 RDB 和 AOF 有什么区别？",
                        "RDB 是某一时刻的数据快照，文件小、恢复快，但可能丢数据；AOF 记录写命令，可配置 always/everysec/no，默认每秒刷盘，丢失窗口小但文件大、恢复慢。生产一般两者混用。"),
                new Question("怎么用 Redis 实现分布式锁？有哪些坑？",
                        "SET key value NX PX 加锁，value 用唯一标识，用 Lua 脚本校验后删除。要注意锁过期业务没执行完（看门狗续期）、误删别人的锁、主从切换丢锁，所以生产更推荐 Redisson 或 RedLock。"),
                new Question("Redis 的过期删除策略和内存淘汰策略？",
                        "过期删除是惰性删除 + 定期抽样删除。内存淘汰有 noeviction、allkeys-lru、allkeys-lfu、volatile-lru、volatile-ttl、random 等，缓存场景一般用 allkeys-lru。")));
        bank.put("项目", List.of(
                new Question("介绍一个你最熟悉的项目，你负责哪部分？",
                        "按「业务背景 → 整体架构 → 我负责的模块 → 技术难点 → 结果数据」来讲，重点突出自己的输出，避免只讲团队做了什么。"),
                new Question("项目中最有挑战的技术问题是什么？你是怎么解决的？",
                        "用 STAR 法则：现象（比如接口 P99 从 200ms 涨到 2s）→ 排查过程（看监控、打日志、压测复现）→ 定位原因 → 解决方案 → 量化结果。"),
                new Question("项目的 QPS 大概多少？你是怎么优化的？",
                        "说清楚量级和压测方式，优化手段按层次讲：缓存（本地 + Redis）、异步化（MQ）、批量合并、SQL 与索引、连接池、代码层面的减少循环内远程调用。"),
                new Question("如果让你重新设计这个项目，你会做哪些改进？",
                        "从可观测性、限流降级、数据一致性、测试覆盖率、部署效率几个角度讲，体现你有复盘和架构思考能力。"),
                new Question("你平时怎么保证代码质量？",
                        "统一规范与 code review、单元测试和集成测试、静态扫描、灰度发布与快速回滚，出问题后写复盘并补测试用例。")));
        bank.put("HR", List.of(
                new Question("为什么想离开现在的公司？",
                        "只谈发展诉求，不谈人际矛盾。例如：希望更深入地做高并发/架构方向，目前业务形态限制了这方面的成长空间。"),
                new Question("你怎么看待加班？",
                        "表达能接受项目关键期的合理加班，同时强调会通过流程和工具提升效率，避免无效加班。"),
                new Question("未来 3 年的职业规划是什么？",
                        "结合岗位说清技术纵深和业务理解两条线，比如一年内熟悉业务并独立负责模块，三年内成为团队的技术骨干。"),
                new Question("你最大的优点和缺点是什么？",
                        "优点要结合岗位要求举例说明；缺点要真实但可控，并说明正在怎么改进。"),
                new Question("期望薪资是多少？",
                        "给出一个区间并说明依据（当前薪资、市场行情、岗位职责），保留谈判空间，同时表达对岗位本身的兴趣。")));
        return bank;
    }

    // -------------------------------------------------------------------- 工具方法

    private static String text(AiRequest request, String key) {
        Object value = request.inputs().get(key);
        return value == null ? "" : value.toString();
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> sortByLengthDesc(List<String> source) {
        return source.stream().sorted(Comparator.comparingInt(String::length).reversed()).toList();
    }

    /** 从文本里抽取命中的关键词，长词优先并「挖空」已命中部分，避免 JavaScript 被误判成 Java。 */
    private static List<String> extract(String text, List<String> dictionary) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String working = text;
        Set<String> found = new LinkedHashSet<>();
        for (String keyword : dictionary) {
            int index = indexOfIgnoreCase(working, keyword);
            if (index >= 0) {
                found.add(keyword);
                working = working.substring(0, index)
                        + " ".repeat(keyword.length())
                        + working.substring(index + keyword.length());
            }
        }
        return new ArrayList<>(found);
    }

    private static int indexOfIgnoreCase(String source, String target) {
        return source.toLowerCase().indexOf(target.toLowerCase());
    }

    private static String extractExperience(String jd) {
        Matcher range = EXPERIENCE_RANGE.matcher(jd);
        if (range.find()) {
            return range.group(1) + "-" + range.group(2) + "年";
        }
        Matcher above = EXPERIENCE_ABOVE.matcher(jd);
        if (above.find()) {
            return above.group(1) + "年以上";
        }
        if (jd.contains("应届")) {
            return "应届";
        }
        if (jd.contains("实习")) {
            return "实习";
        }
        return "不限";
    }

    private static String extractEducation(String jd) {
        for (String level : List.of("博士", "硕士", "研究生", "本科", "大专", "专科")) {
            if (jd.contains(level)) {
                return "研究生".equals(level) ? "硕士" : ("专科".equals(level) ? "大专" : level);
            }
        }
        return "不限";
    }

    private static String guessSeniority(String experience) {
        Matcher matcher = Pattern.compile("(\\d+)").matcher(experience);
        if (!matcher.find()) {
            return "中级";
        }
        int years = Integer.parseInt(matcher.group(1));
        if (years <= 1) {
            return "初级";
        }
        if (years <= 3) {
            return "中级";
        }
        if (years <= 6) {
            return "高级";
        }
        return "专家";
    }
}
