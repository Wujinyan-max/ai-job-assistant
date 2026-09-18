package com.jobassistant.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobassistant.vo.ResumeStructureVO;
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
            case RESUME_OPTIMIZE -> optimizeResume(request);
            case RESUME_STRUCTURE -> structureResume(request);
            // 视觉识别没有本地实现：没配 API Key 时不走这条路，真走到这里就退回规则解析
            case RESUME_VISION -> structureResume(request);
            case INTERVIEW_QUESTION -> generateQuestions(request);
            case REVIEW_QUESTIONS -> extractQuestionsFromReview(request);
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

    // -------------------------------------------------------------- 简历专项优化

    /**
     * 句首弱动词 → 结果导向的说法。
     * <p>本地引擎只换表达、不替候选人编造内容，所以换完动词后会把「缺量化结果」的问题
     * 作为占位提示补回去，让用户自己填数据。</p>
     */
    private static final List<Map.Entry<String, String>> WEAK_VERBS = List.of(
            Map.entry("负责", "主导"),
            Map.entry("参与", "深度参与"),
            Map.entry("协助", "协同推进"),
            Map.entry("帮助", "推动"),
            Map.entry("完成", "交付"),
            Map.entry("做了", "落地实现"));

    /** 简历里常见的板块标题，用来给每条改写打上板块标签 */
    private static final Pattern SECTION_HEADING = Pattern.compile(
            "^[▮■▪●◆◇□▸▶*·•\\s-]*(?:[一二三四五六七八九十0-9]+\\s*[、.．)]\\s*)?"
                    + "(项目经历|项目经验|工作经历|实习经历|教育经历|教育背景|专业技能|技能|个人简介|自我评价)\\s*[:：]?$");

    /** 结构化简历经常把板块名和值放在同一行，例如「技能：Java、Redis」。 */
    private static final Pattern LABELED_LINE = Pattern.compile(
            "^[▮■▪●◆◇□▸▶*·•\\s-]*(姓名|学历|工作年限|技能|专业技能|个人简介|自我评价|教育经历|教育背景|简历正文|主修课程)\\s*[:：]\\s*(.*)$");

    private static final Pattern QUANTIFIED = Pattern.compile("\\d|%|qps|ms|万|亿|倍", Pattern.CASE_INSENSITIVE);

    private static final Pattern LINE_PREFIX = Pattern.compile("^\\s*(?:\\d+[.、)]|[-*·])\\s*");

    /** 只有描述经历和成果的板块才提示补量化数据：给「技能」这种罗列行加数据占位毫无意义 */
    private static final Set<String> QUANTIFIABLE_SECTIONS =
            Set.of("项目经历", "项目经验", "工作经历", "实习经历");

    /** 太短的行多半是标题或零碎词，不值得改写 */
    private static final int MIN_REWRITE_LENGTH = 6;

    /** 一条素材最多改写多少条，避免用户贴整份简历时结果长到没法看 */
    private static final int MAX_REWRITES = 12;

    private Map<String, Object> optimizeResume(AiRequest request) {
        String jd = text(request, "jd");
        String raw = text(request, "resume");

        Set<String> required = new LinkedHashSet<>(extract(jd, TECH_BY_LENGTH));
        required.addAll(extract(jd, CN_BY_LENGTH));
        Set<String> owned = new LinkedHashSet<>(extract(raw, TECH_BY_LENGTH));
        owned.addAll(extract(raw, CN_BY_LENGTH));

        List<String> matched = required.stream().filter(owned::contains).toList();
        List<String> missing = required.stream().filter(skill -> !owned.contains(skill)).toList();

        List<Map<String, Object>> rewrites = new ArrayList<>();
        int contentLines = 0;
        for (ResumeLine line : sectionedLines(raw)) {
            String trimmed = line.text();
            if (trimmed.length() < MIN_REWRITE_LENGTH) {
                continue;
            }
            contentLines++;
            String optimized = rewriteLine(line.section(), trimmed);
            // 没有实际改动的句子不进改写列表：并排展示两条一样的文案只会干扰阅读
            if (optimized == null || rewrites.size() >= MAX_REWRITES) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("section", line.section());
            item.put("original", trimmed);
            item.put("optimized", optimized);
            item.put("reason", rewriteReason(trimmed));
            rewrites.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rewrites", rewrites);
        result.put("matchedKeywords", matched);
        result.put("missingKeywords", missing);
        result.put("suggestions", optimizeSuggestions(rewrites, missing));
        result.put("optimizedContent", buildOptimizedContent(rewrites, owned, required, missing));
        result.put("comment", optimizeComment(rewrites, contentLines, matched.size(), required.size()));
        result.put("newProjects", buildMockNewProjects(missing));
        return result;
    }

    private record ResumeLine(String section, String text) {
    }

    /** 优先复用排版解析器的板块判断；没有标题时才回退到原来的逐行规则。 */
    private static List<ResumeLine> sectionedLines(String raw) {
        List<ResumeStructureParser.ClassifiedSection> classified = ResumeStructureParser.classifySections(raw);
        if (!classified.isEmpty()) {
            List<ResumeLine> lines = new ArrayList<>();
            for (ResumeStructureParser.ClassifiedSection block : classified) {
                String section = sectionLabel(block.key());
                for (String text : block.lines()) {
                    if (!text.isBlank()) {
                        lines.add(new ResumeLine(section, text.trim()));
                    }
                }
            }
            return lines;
        }

        List<ResumeLine> lines = new ArrayList<>();
        String section = "项目经历";
        for (String rawLine : raw.split("\\R")) {
            String text = rawLine.trim();
            if (text.isEmpty()) {
                continue;
            }
            Matcher heading = SECTION_HEADING.matcher(text);
            if (heading.matches()) {
                section = normalizeSection(heading.group(1));
                continue;
            }
            Matcher labeled = LABELED_LINE.matcher(text);
            if (labeled.matches()) {
                section = sectionForLabel(labeled.group(1), section);
                text = labeled.group(2).trim();
            }
            if (!text.isEmpty()) {
                lines.add(new ResumeLine(section, text));
            }
        }
        return lines;
    }

    private static String sectionLabel(String key) {
        return switch (key) {
            case "education" -> "教育经历";
            case "work" -> "工作经历";
            case "projects" -> "项目经历";
            case "skills" -> "技能";
            case "honors" -> "荣誉证书";
            case "summary" -> "自我评价";
            default -> key;
        };
    }

    private static String normalizeSection(String section) {
        return "教育背景".equals(section) ? "教育经历" : section;
    }

    private static String sectionForLabel(String label, String currentSection) {
        return switch (label) {
            case "技能", "专业技能" -> "技能";
            case "个人简介", "自我评价" -> label;
            case "学历", "教育经历", "教育背景", "主修课程" -> "教育经历";
            case "姓名", "工作年限" -> "基本信息";
            case "简历正文" -> "项目经历";
            default -> currentSection;
        };
    }

    /** 返回改写后的表达；这句话已经没有可改的地方时返回 null */
    private static String rewriteLine(String section, String line) {
        String rewritten = LINE_PREFIX.matcher(line).replaceFirst("");
        boolean changed = !rewritten.equals(line);
        for (Map.Entry<String, String> entry : WEAK_VERBS) {
            if (rewritten.startsWith(entry.getKey())) {
                rewritten = entry.getValue() + rewritten.substring(entry.getKey().length());
                changed = true;
                break;
            }
        }
        if (QUANTIFIABLE_SECTIONS.contains(section) && !QUANTIFIED.matcher(rewritten).find()) {
            rewritten = rewritten + "，【待补充：量化结果，如 QPS / 耗时 / 提升幅度】";
            changed = true;
        }
        return changed ? rewritten : null;
    }

    private static String rewriteReason(String line) {
        String stripped = LINE_PREFIX.matcher(line).replaceFirst("");
        for (Map.Entry<String, String> entry : WEAK_VERBS) {
            if (stripped.startsWith(entry.getKey())) {
                return "句首「" + entry.getKey() + "」偏弱，换成结果导向的动词更有说服力";
            }
        }
        if (!QUANTIFIED.matcher(line).find()) {
            return "只有过程没有结果，补上量化数据 HR 才能判断项目复杂度";
        }
        if (!extract(line, TECH_BY_LENGTH).isEmpty()) {
            return "技术点保留，把与岗位最相关的技术栈前置到句首";
        }
        return "表述偏流水账，收拢成「做了什么 + 拿到什么结果」";
    }

    private static List<String> optimizeSuggestions(List<Map<String, Object>> rewrites, List<String> missing) {
        List<String> suggestions = new ArrayList<>();
        missing.stream().limit(3).forEach(skill ->
                suggestions.add("补充「" + skill + "」相关的项目产出，写清你负责的部分和最后的结果"));
        if (rewrites.isEmpty()) {
            suggestions.add("先补齐项目经历：项目背景、你的职责、技术方案、最终结果，每段至少写一句");
        } else if (rewrites.stream()
                .anyMatch(item -> !QUANTIFIED.matcher((String) item.get("original")).find())) {
            suggestions.add("每条经历都补量化结果：QPS、耗时、成本、成功率，给区间也比没有强");
        }
        suggestions.add("把与目标岗位无关的技术细节删掉，把 JD 里的关键词挪到每条的开头");
        return suggestions.stream().limit(4).toList();
    }

    private static List<Map<String, Object>> buildMockNewProjects(List<String> missing) {
        if (missing == null || missing.isEmpty()) {
            return List.of();
        }
        String skill = missing.get(0);
        Map<String, Object> project = new LinkedHashMap<>();
        project.put("title", "示例项目（本地模拟）");
        project.put("role", "核心开发");
        project.put("description", "覆盖「" + skill + "」的示例项目，配置 API Key 后由 AI 生成真实内容");
        project.put("techStack", List.of(skill));
        project.put("bullets", List.of("示例：负责" + skill + "相关模块的设计与落地"));
        project.put("fabricated", true);
        return List.of(project);
    }

    private static String buildOptimizedContent(List<Map<String, Object>> rewrites, Set<String> owned,
                                                Set<String> required, List<String> missing) {
        StringBuilder content = new StringBuilder();
        // 岗位要求的技能排在前面，HR 第一眼看到的才是与岗位相关的能力
        List<String> skills = new ArrayList<>(owned);
        skills.sort(Comparator.comparingInt((String skill) -> required.contains(skill) ? 0 : 1));
        if (!skills.isEmpty()) {
            content.append("技能：").append(String.join("、", skills)).append('\n');
        }
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> item : rewrites) {
            grouped.computeIfAbsent((String) item.get("section"), key -> new ArrayList<>())
                    .add("- " + item.get("optimized"));
        }
        grouped.forEach((name, lines) -> {
            content.append(name).append("：\n");
            lines.forEach(line -> content.append(line).append('\n'));
        });
        if (!missing.isEmpty()) {
            content.append("【待补充】")
                    .append(String.join("、", missing.stream().limit(5).toList()))
                    .append("：补上对应的项目产出与量化结果\n");
        }
        return content.toString();
    }

    private static String optimizeComment(List<Map<String, Object>> rewrites, int contentLines,
                                          int matchedCount, int requiredCount) {
        if (rewrites.isEmpty()) {
            return contentLines == 0
                    ? "没有识别到可以改写的经历描述，先补充「做了什么 + 拿到什么结果」的项目细节，再来优化会更有效。"
                    : "素材里这几句已经比较具体，没有识别到需要改写的表达。先按下面的清单补齐关键词，再回来优化会更有效。";
        }
        String coverage = requiredCount == 0
                ? "JD 里没有识别到具体的技术关键词"
                : "命中岗位关键词 " + matchedCount + "/" + requiredCount + " 个";
        return "共改写 " + rewrites.size() + " 条，" + coverage
                + "。本轮由本地模拟引擎按规则改写，配置 API Key 后可以得到更贴合岗位的语义级优化。";
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
            String normalized = QuestionCategory.normalize(category);
            List<Question> bank = bankOf(normalized);
            for (int i = 0; i < Math.min(count, bank.size()); i++) {
                Question q = bank.get(i);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("category", normalized);
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

    /**
     * 固定分类到模拟题库的映射：模拟题库是按技术点分桶的，一个固定分类可能对应多个桶
     * （例如「数据库与缓存」同时命中 MySQL 和 Redis），这里合并后再取题。
     */
    private static List<Question> bankOf(String category) {
        List<String> keys = BANK_KEYS_BY_CATEGORY.getOrDefault(category, List.of("项目"));
        List<Question> merged = new ArrayList<>();
        for (String key : keys) {
            List<Question> bank = QUESTION_BANK.get(key);
            if (bank != null) {
                merged.addAll(bank);
            }
        }
        return merged;
    }

    private List<String> suggestCategories(String jd) {
        Set<String> skills = new LinkedHashSet<>(extract(jd, TECH_BY_LENGTH));
        Set<String> categories = new LinkedHashSet<>();
        if (skills.contains("Java") || skills.contains("JVM")) {
            categories.add(QuestionCategory.LANGUAGE.label());
        }
        if (skills.contains("Spring Boot") || skills.contains("Spring Cloud") || skills.contains("Spring MVC")) {
            categories.add(QuestionCategory.FRAMEWORK.label());
        }
        if (skills.contains("MySQL") || skills.contains("PostgreSQL") || skills.contains("Oracle")) {
            categories.add(QuestionCategory.DATABASE.label());
        }
        if (skills.contains("Redis")) {
            categories.add(QuestionCategory.DATABASE.label());
        }
        categories.add(QuestionCategory.PROJECT.label());
        categories.add(QuestionCategory.HR.label());
        return categories.stream().limit(5).toList();
    }

    private record Question(String question, String answer) {
    }

    private static final Map<String, List<Question>> QUESTION_BANK = buildQuestionBank();

    private static final Map<String, List<String>> BANK_KEYS_BY_CATEGORY = Map.of(
            "编程语言与基础", List.of("Java基础"),
            "框架与中间件", List.of("Spring Boot"),
            "数据库与缓存", List.of("MySQL", "Redis"),
            "系统设计与性能", List.of("项目"),
            "测试与质量", List.of("项目"),
            "项目与业务", List.of("项目"),
            "HR与软素质", List.of("HR"));

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

    // ---------------------------------------------------------------- 简历结构化

    /**
     * 简历结构化：把纯文本简历（含 PDF 复制出来的「标题被挤到正文后面」的排版）识别成固定 JSON 结构。
     * 规则都在 {@link ResumeStructureParser} 里，这里只负责转成与真实模型一致的 Map。
     */
    private Map<String, Object> structureResume(AiRequest request) {
        ResumeStructureVO vo = ResumeStructureParser.parse(text(request, "resume"));
        return objectMapper.convertValue(vo, new TypeReference<Map<String, Object>>() {
        });
    }
    // -------------------------------------------------------------------- 工具方法

    // ------------------------------------------------------------ 面试复盘提取题目

    /** 复盘里常见的「这是面试官问的」信号词 */
    private static final List<String> ASK_MARKERS = List.of(
            "被问到", "问到了", "问了我", "问的是", "面试官问", "提问", "考点", "题目", "问题");

    /** 「被问到 XXX」「问了 XXX」这类句式里，真正的问题从这些词开始 */
    private static final Pattern QUESTION_SPLIT = Pattern.compile("[?？]");

    private static final Pattern LEADING_NOISE = Pattern.compile(
            "^\\s*(?:\\d+[.、)]|[-*·]|第\\s*\\d+\\s*[轮次]?[：:]?|面试官|hr|HR)\\s*[：:，,、]?\\s*");

    /**
     * 从复盘文本里提取面试题。
     * <p>本地引擎只能做规则抽取：优先捞带问号的句子和「被问到 XXX」的句式，
     * 捞不到时再退化成按已知技术关键词组题，保证这个按钮在没配 Key 时也有产出。</p>
     */
    private Map<String, Object> extractQuestionsFromReview(AiRequest request) {
        String review = text(request, "review");
        List<Map<String, Object>> questions = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (String raw : review.split("\\R|(?<=[。；;])")) {
            String line = raw.strip();
            if (line.length() < 6) {
                continue;
            }
            for (String piece : QUESTION_SPLIT.split(line)) {
                String question = cleanQuestion(piece);
                if (question == null || !seen.add(question)) {
                    continue;
                }
                questions.add(buildReviewQuestion(question));
            }
        }

        // 复盘只写了知识点、没写问句时，用命中的技术关键词补题
        if (questions.isEmpty()) {
            for (String keyword : extract(review, TECH_BY_LENGTH)) {
                String question = "请讲讲「" + keyword + "」的原理和常见考点";
                if (seen.add(question)) {
                questions.add(buildReviewQuestion(question));
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("questions", questions);
        return result;
    }

    /** 把一条复盘片段整理成完整问句，不像问题的片段返回 null */
    private String cleanQuestion(String piece) {
        String text = LEADING_NOISE.matcher(piece.strip()).replaceFirst("").strip();
        text = text.replaceFirst("^(?:被|我)?(?:问到|问了|问的是|问了问)[：:，,]?\\s*", "");
        if (text.length() < 6 || text.length() > 120) {
            return null;
        }
        boolean looksLikeQuestion = piece.contains("?") || piece.contains("？")
                || ASK_MARKERS.stream().anyMatch(text::contains)
                || containsAny(text, "什么", "如何", "怎么", "为什么", "区别", "原理", "介绍", "说说", "讲讲");
        if (!looksLikeQuestion) {
            return null;
        }
        return text.endsWith("？") || text.endsWith("?") ? text : text + "？";
    }

    private Map<String, Object> buildReviewQuestion(String question) {
        String category = QuestionCategory.normalize(firstKeyword(question));
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("category", category);
        item.put("question", question);
        item.put("difficulty", "MEDIUM");
        item.put("answer", "结合本次面试复盘的记录补充答案：先讲清核心概念与原理，"
                + "再结合自己做过的项目说明实际用法，最后补上这次没答好的点。");
        return item;
    }

    private String firstKeyword(String question) {
        for (String keyword : TECH_BY_LENGTH) {
            if (indexOfIgnoreCase(question, keyword) >= 0) {
                return keyword;
            }
        }
        for (String keyword : CN_BY_LENGTH) {
            if (question.contains(keyword)) {
                return keyword;
            }
        }
        return question;
    }

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
