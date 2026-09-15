package com.jobassistant.service.impl;

import org.springframework.util.StringUtils;

import java.time.Year;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 简历纯文本的字段抽取规则。
 * <p>中文简历写法千差万别，这里只做「读出来就能用」的保守推断：
 * 抽不到就留空交给用户补，宁可少填也不填错。</p>
 */
public final class ResumeFieldExtractor {

    private static final Pattern PHONE = Pattern.compile("(?<![0-9-])(1[3-9]\\d{9})(?![0-9])");
    private static final Pattern LANDLINE = Pattern.compile("(?<![0-9])(0\\d{2,3}[- ]?\\d{7,8})(?![0-9])");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private static final Pattern LABELED_NAME =
            Pattern.compile("姓\\s*名\\s*[:：]\\s*([\\u4e00-\\u9fa5·]{2,6})");
    private static final Pattern LINE_NAME =
            Pattern.compile("^([\\u4e00-\\u9fa5·]{2,4})(?=$|[\\s|｜,，.。·:：/])");
    private static final Pattern INTENT = Pattern.compile(
            "(?:求职意向|应聘职位|期望职位|期望岗位|目标职位|意向岗位|求职岗位)\\s*[:：]\\s*([^\\n，,。;；|｜]{2,30})");
    private static final Pattern WORK_YEARS =
            Pattern.compile("(\\d{1,2})\\s*年(?:以上|多)?(?:工作)?经[验历]");
    private static final Pattern WORK_RANGE = Pattern.compile(
            "((?:19|20)\\d{2})\\s*[年.\\-/]?\\s*\\d{0,2}\\s*月?\\s*(?:[-–—~至到])\\s*(?:至今|现在|now|(?:19|20)\\d{2})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern GRADUATE_YEAR =
            Pattern.compile("((?:19|20)\\d{2})\\s*年\\s*毕业");

    /** 段落标题，用来切分「自我评价」「工作经历」这类区块 */
    private static final String HEADERS = "教育背景|教育经历|教育情况|学历|工作经历|工作经验|职业经历|工作履历|项目经历"
            + "|项目经验|实习经历|专业技能|技能特长|技能清单|技能|技术栈|荣誉证书|荣誉|获奖情况|获奖|自我评价"
            + "|个人评价|个人优势|个人总结|自我介绍|求职意向|联系方式|个人信息|基本资料|基本信息";
    private static final Pattern SECTION_START = Pattern.compile("\\n[ \\t]*(?:" + HEADERS + ")[ \\t]*[:：]?");
    /** 一行开头就是段落标题（「技能：Java」「技能 Java」「技能」都算），这种行不能当成姓名 */
    private static final Pattern SECTION_HEADER_LINE = Pattern.compile("^(?:" + HEADERS + ")(?:[:：]|\\s|$)");
    private static final Pattern SUMMARY_SECTION = Pattern.compile(
            "(?:自我评价|个人简介|个人评价|个人优势|个人总结|自我介绍|个人描述)\\s*[:：]?[ \\t]*\\n?");
    private static final Pattern WORK_SECTION = Pattern.compile("(?:工作经历|工作经验|职业经历|工作履历)");

    /** 学历关键词，顺序即优先级（博士 > 硕士 > 本科 > 大专） */
    private static final String[][] EDUCATION_KEYWORDS = {
            {"博士", "博士"},
            {"硕士", "硕士"},
            {"研究生", "硕士"},
            {"本科", "本科"},
            {"学士", "本科"},
            {"大专", "大专"},
            {"专科", "大专"}
    };

    private static final Set<String> NAME_STOP_WORDS = Set.of(
            "个人简历", "简历", "个人信息", "基本信息", "基本资料", "个人资料", "教育经历", "教育背景",
            "工作经历", "工作经验", "项目经历", "项目经验", "技能特长", "专业技能", "自我评价", "个人简介",
            "求职意向", "联系方式", "荣誉证书", "自我介绍", "个人优势",
            // PDF 抽出来的文本常把标签和值拆成两行，这些标签不能当成姓名
            "姓名", "性别", "电话", "手机", "手机号", "邮箱", "邮箱地址", "年龄", "出生年月", "民族", "籍贯",
            "政治面貌", "住址", "现居", "专业", "学校", "毕业院校");

    private static final int MAX_SKILLS = 20;
    private static final int MAX_SKILL_TEXT = 1000;
    private static final int MAX_SUMMARY_TEXT = 300;

    /** 技能标签：别名 -> 展示名，命中任意别名即算掌握 */
    private static final Map<String, List<Pattern>> SKILL_PATTERNS = buildSkillPatterns();

    private ResumeFieldExtractor() {
    }

    /**
     * 从简历文件里抽出来的字段，抽不到的为 null。
     */
    public record ParsedFields(String name, String phone, String email, String education,
                               Integer workYears, String skills, String summary, String intent) {
    }

    /** 统一换行、去掉不可见字符和每行首尾的空格，避免 PDF/DOCX 抽出来的文本带着脏字符进数据库 */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.replace("\r\n", "\n").replace('\r', '\n')
                .replace('\u00a0', ' ')
                .replace('\u3000', ' ')
                .replace("\t", " ");
        if (text.startsWith("\ufeff")) {
            text = text.substring(1);
        }
        text = text.replaceAll("[ ]+\n", "\n")
                .replaceAll("\n[ ]+", "\n")
                .replaceAll("\n{3,}", "\n\n");
        return text.strip();
    }

    public static ParsedFields extract(String text) {
        if (!StringUtils.hasText(text)) {
            return new ParsedFields(null, null, null, null, null, null, null, null);
        }
        return new ParsedFields(
                extractName(text),
                extractPhone(text),
                extractEmail(text),
                extractEducation(text),
                extractWorkYears(text),
                extractSkills(text),
                extractSummary(text),
                extractIntent(text));
    }

    private static String extractName(String text) {
        Matcher labeled = LABELED_NAME.matcher(text);
        if (labeled.find()) {
            return labeled.group(1);
        }
        // 很多简历第一行就是姓名，后面跟着「男 | 5年经验」这类信息
        String[] lines = text.split("\n");
        for (int i = 0; i < Math.min(lines.length, 5); i++) {
            String line = lines[i].strip();
            Matcher matcher = LINE_NAME.matcher(line);
            if (matcher.find()) {
                String candidate = matcher.group(1);
                if (!NAME_STOP_WORDS.contains(candidate) && !SECTION_HEADER_LINE.matcher(line).find()) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static String extractPhone(String text) {
        Matcher mobile = PHONE.matcher(text);
        if (mobile.find()) {
            return mobile.group(1);
        }
        Matcher landline = LANDLINE.matcher(text);
        return landline.find() ? landline.group(1) : null;
    }

    private static String extractEmail(String text) {
        Matcher matcher = EMAIL.matcher(text);
        return matcher.find() ? matcher.group() : null;
    }

    private static String extractEducation(String text) {
        for (String[] pair : EDUCATION_KEYWORDS) {
            if (text.contains(pair[0])) {
                return pair[1];
            }
        }
        return null;
    }

    private static String extractIntent(String text) {
        Matcher matcher = INTENT.matcher(text);
        return matcher.find() ? matcher.group(1).strip() : null;
    }

    private static Integer extractWorkYears(String text) {
        Matcher explicit = WORK_YEARS.matcher(text);
        if (explicit.find()) {
            return clampYears(Integer.parseInt(explicit.group(1)));
        }
        // 没有写「N 年经验」时，用工作经历里最早的一段起始年份倒推
        Integer startYear = earliestStartYear(sectionText(text, WORK_SECTION));
        if (startYear == null) {
            startYear = earliestStartYear(text);
        }
        if (startYear == null) {
            Matcher graduate = GRADUATE_YEAR.matcher(text);
            startYear = graduate.find() ? Integer.parseInt(graduate.group(1)) : null;
        }
        return startYear == null ? null : clampYears(Year.now().getValue() - startYear);
    }

    private static Integer earliestStartYear(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        Matcher matcher = WORK_RANGE.matcher(text);
        Integer earliest = null;
        while (matcher.find()) {
            int year = Integer.parseInt(matcher.group(1));
            if (earliest == null || year < earliest) {
                earliest = year;
            }
        }
        return earliest;
    }

    private static Integer clampYears(int years) {
        if (years < 0) {
            return 0;
        }
        return Math.min(years, 40);
    }

    private static String extractSkills(String text) {
        Set<String> found = new LinkedHashSet<>();
        for (Map.Entry<String, List<Pattern>> entry : SKILL_PATTERNS.entrySet()) {
            if (found.size() >= MAX_SKILLS) {
                break;
            }
            for (Pattern pattern : entry.getValue()) {
                if (pattern.matcher(text).find()) {
                    found.add(entry.getKey());
                    break;
                }
            }
        }
        StringBuilder builder = new StringBuilder();
        for (String skill : found) {
            if (builder.length() + skill.length() + 1 > MAX_SKILL_TEXT) {
                break;
            }
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(skill);
        }
        return builder.length() == 0 ? null : builder.toString();
    }

    private static String extractSummary(String text) {
        Matcher section = SUMMARY_SECTION.matcher(text);
        if (section.find()) {
            String body = cutAtNextSection(text.substring(section.end())).strip();
            if (body.length() >= 10) {
                return trimTo(body, MAX_SUMMARY_TEXT);
            }
        }
        // 兜底：取第一段像正文的段落
        for (String paragraph : text.split("\n{2,}")) {
            String line = paragraph.strip().replaceAll("\n", " ");
            if (line.length() >= 30 && !looksLikeContact(line)) {
                return trimTo(line, MAX_SUMMARY_TEXT);
            }
        }
        return null;
    }

    private static String sectionText(String text, Pattern header) {
        Matcher matcher = header.matcher(text);
        if (!matcher.find()) {
            return "";
        }
        return cutAtNextSection(text.substring(matcher.end()));
    }

    private static String cutAtNextSection(String text) {
        Matcher next = SECTION_START.matcher(text);
        return next.find() ? text.substring(0, next.start()) : text;
    }

    private static boolean looksLikeContact(String line) {
        return line.contains("@") || PHONE.matcher(line).find()
                || line.startsWith("姓名") || line.startsWith("电话") || line.startsWith("手机")
                || line.startsWith("邮箱");
    }

    private static String trimTo(String value, int max) {
        String trimmed = value.strip();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static Map<String, List<Pattern>> buildSkillPatterns() {
        Map<String, String[]> skills = new LinkedHashMap<>();
        skills.put("Java", new String[]{"Java"});
        skills.put("Spring Boot", new String[]{"Spring Boot", "SpringBoot"});
        skills.put("Spring Cloud", new String[]{"Spring Cloud", "SpringCloud"});
        skills.put("MyBatis", new String[]{"MyBatis"});
        skills.put("MyBatis-Plus", new String[]{"MyBatis-Plus", "MyBatisPlus"});
        skills.put("Hibernate", new String[]{"Hibernate"});
        skills.put("MySQL", new String[]{"MySQL"});
        skills.put("PostgreSQL", new String[]{"PostgreSQL"});
        skills.put("Oracle", new String[]{"Oracle"});
        skills.put("MongoDB", new String[]{"MongoDB"});
        skills.put("Redis", new String[]{"Redis"});
        skills.put("Elasticsearch", new String[]{"Elasticsearch", "ElasticSearch", "ES 搜索"});
        skills.put("Kafka", new String[]{"Kafka"});
        skills.put("RocketMQ", new String[]{"RocketMQ"});
        skills.put("RabbitMQ", new String[]{"RabbitMQ"});
        skills.put("Dubbo", new String[]{"Dubbo"});
        skills.put("Nacos", new String[]{"Nacos"});
        skills.put("Zookeeper", new String[]{"Zookeeper", "ZooKeeper"});
        skills.put("Netty", new String[]{"Netty"});
        skills.put("JVM", new String[]{"JVM"});
        skills.put("Docker", new String[]{"Docker"});
        skills.put("Kubernetes", new String[]{"Kubernetes", "K8s", "K8S"});
        skills.put("Linux", new String[]{"Linux"});
        skills.put("Shell", new String[]{"Shell", "Linux 脚本"});
        skills.put("Nginx", new String[]{"Nginx"});
        skills.put("Git", new String[]{"Git"});
        skills.put("Maven", new String[]{"Maven"});
        skills.put("Jenkins", new String[]{"Jenkins"});
        skills.put("分布式", new String[]{"分布式"});
        skills.put("微服务", new String[]{"微服务"});
        skills.put("高并发", new String[]{"高并发"});
        skills.put("性能优化", new String[]{"性能优化", "性能调优"});
        skills.put("Python", new String[]{"Python"});
        skills.put("Go", new String[]{"Go 语言", "Golang"});
        skills.put("C++", new String[]{"C++"});
        skills.put("C#", new String[]{"C#"});
        skills.put(".NET", new String[]{".NET"});
        skills.put("Node.js", new String[]{"Node.js", "Nodejs"});
        skills.put("Vue", new String[]{"Vue", "Vue.js", "Vue3"});
        skills.put("React", new String[]{"React"});
        skills.put("TypeScript", new String[]{"TypeScript"});
        skills.put("JavaScript", new String[]{"JavaScript"});
        skills.put("HTML", new String[]{"HTML"});
        skills.put("CSS", new String[]{"CSS"});
        skills.put("小程序", new String[]{"小程序"});
        skills.put("Spark", new String[]{"Spark"});
        skills.put("Flink", new String[]{"Flink"});
        skills.put("Hive", new String[]{"Hive"});
        skills.put("Hadoop", new String[]{"Hadoop"});
        skills.put("ClickHouse", new String[]{"ClickHouse"});
        skills.put("TensorFlow", new String[]{"TensorFlow"});
        skills.put("PyTorch", new String[]{"PyTorch"});
        skills.put("机器学习", new String[]{"机器学习"});
        skills.put("深度学习", new String[]{"深度学习"});
        skills.put("大模型", new String[]{"大模型", "LLM", "大语言模型"});
        skills.put("微服务治理", new String[]{"服务治理"});
        skills.put("单元测试", new String[]{"单元测试"});
        skills.put("自动化测试", new String[]{"自动化测试", "Selenium"});
        skills.put("JMeter", new String[]{"JMeter"});
        skills.put("SQL", new String[]{"SQL"});

        Map<String, List<Pattern>> patterns = new LinkedHashMap<>();
        skills.forEach((label, aliases) -> {
            List<Pattern> compiled = new ArrayList<>();
            for (String alias : aliases) {
                compiled.add(aliasPattern(alias));
            }
            patterns.put(label, compiled);
        });
        return patterns;
    }

    /** 英文技能要卡住边界，否则 JavaScript 会被当成 Java */
    private static Pattern aliasPattern(String alias) {
        boolean asciiOnly = alias.chars().allMatch(c -> c < 128);
        String quoted = Pattern.quote(alias);
        String regex = asciiOnly
                ? "(?<![A-Za-z0-9+#.\\-])" + quoted + "(?![A-Za-z0-9+#.\\-])"
                : quoted;
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }
}
