package com.jobassistant.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 没有配置 ai.api-key 时走的就是这个本地模拟引擎，
 * 它必须产出与真实模型一致的结构，否则降级路径会直接崩掉。
 */
class MockAiEngineTest {

    private static final String JD = """
            岗位职责：
            1. 负责核心交易系统的后端设计与开发
            2. 参与高并发场景下的性能优化与稳定性治理

            任职要求：
            1. 本科及以上学历，3-5年 Java 后端开发经验
            2. 精通 Java，熟悉 JVM 原理与多线程并发编程
            3. 熟练使用 Spring Boot、MyBatis
            4. 熟悉 MySQL 索引优化与 Redis 缓存设计，了解 Kafka 消息队列
            5. 有分布式系统、高并发项目经验者优先
            """;

    private static final String RESUME = """
            3 年 Java 后端开发经验，熟悉 Spring Boot、MyBatis、MySQL。
            负责订单系统的重构，引入缓存后接口耗时从 800ms 降到 120ms。
            """;

    /** 用户手写的原始素材：板块标题 + 项目经历 + 技能，是「简历优化」的典型输入 */
    private static final String RAW_MATERIAL = """
            个人简介
            3 年 Java 后端开发经验，主要做交易和订单方向。

            项目经历
            1. 负责订单系统的重构，引入 Redis 缓存后接口耗时从 800ms 降到 120ms
            2. 参与支付链路的稳定性治理

            技能
            Java、Spring Boot、MyBatis、MySQL
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MockAiEngine engine = new MockAiEngine(objectMapper);

    private JsonNode reply(AiTask task, Map<String, Object> inputs) throws Exception {
        return objectMapper.readTree(engine.reply(new AiRequest(task, "system", "user", inputs)));
    }

    @Test
    @DisplayName("JD 解析：抽得出技能、经验年限和学历")
    void analyzeJd() throws Exception {
        JsonNode result = reply(AiTask.JD_ANALYZE, Map.of("jd", JD));

        List<String> skills = toStringList(result.path("skills"));
        assertThat(skills).contains("Java", "Spring Boot", "MyBatis", "MySQL", "Redis", "Kafka", "JVM");

        assertThat(result.path("experience").asText()).isEqualTo("3-5年");
        assertThat(result.path("education").asText()).isEqualTo("本科");
        assertThat(toStringList(result.path("keywords"))).isNotEmpty();
        assertThat(toStringList(result.path("responsibilities"))).isNotEmpty();
    }

    @Test
    @DisplayName("关键词抽取按长词优先，JavaScript 不会被误判成 Java")
    void longerKeywordWins() throws Exception {
        JsonNode result = reply(AiTask.JD_ANALYZE,
                Map.of("jd", "要求熟悉 JavaScript 与 Node.js，能独立完成前端页面开发"));

        List<String> skills = toStringList(result.path("skills"));
        assertThat(skills).contains("JavaScript", "Node.js");
        assertThat(skills).doesNotContain("Java");
    }

    @Test
    @DisplayName("简历匹配：分数在 0-100 之间，缺失技能能识别出来")
    void matchResume() throws Exception {
        JsonNode result = reply(AiTask.RESUME_MATCH, Map.of("jd", JD, "resume", RESUME));

        int score = result.path("score").asInt();
        assertThat(score).isBetween(0, 100);

        assertThat(toStringList(result.path("matchedSkills"))).contains("Java", "Spring Boot", "MySQL");
        List<String> missing = toStringList(result.path("missingSkills"));
        assertThat(missing).contains("Redis", "Kafka");

        assertThat(toStringList(result.path("strengths"))).isNotEmpty();
        assertThat(toStringList(result.path("suggestions"))).isNotEmpty();
    }

    @Test
    @DisplayName("简历匹配：JD 里没有任何技术词时给中性分，不做除零")
    void matchResumeWithEmptyJd() throws Exception {
        JsonNode result = reply(AiTask.RESUME_MATCH, Map.of("jd", "我们是一家很有前景的公司", "resume", RESUME));
        assertThat(result.path("score").asInt()).isEqualTo(60);
    }

    @Test
    @DisplayName("出题：按指定分类返回，每题都带答案与难度")
    void generateQuestionsWithGivenCategories() throws Exception {
        JsonNode result = reply(AiTask.INTERVIEW_QUESTION, Map.of(
                "jd", JD,
                "categories", List.of("编程语言与基础", "数据库与缓存"),
                "count", 2,
                "difficulty", "HARD"));

        JsonNode questions = result.path("questions");
        assertThat(questions).hasSize(4);
        for (JsonNode question : questions) {
            assertThat(question.path("question").asText()).isNotBlank();
            assertThat(question.path("answer").asText()).isNotBlank();
            assertThat(question.path("category").asText()).isIn("编程语言与基础", "数据库与缓存");
            assertThat(question.path("difficulty").asText()).isEqualTo("HARD");
        }
    }

    @Test
    @DisplayName("出题：不指定分类时根据 JD 里的技术栈自动推荐，最多 5 个分类")
    void generateQuestionsSuggestsCategories() throws Exception {
        JsonNode result = reply(AiTask.INTERVIEW_QUESTION, Map.of("jd", JD));

        JsonNode questions = result.path("questions");
        assertThat(questions).isNotEmpty();
        assertThat(questions.size()).isLessThanOrEqualTo(15);

        List<String> categories = new java.util.ArrayList<>();
        questions.forEach(q -> categories.add(q.path("category").asText()));
        assertThat(categories).contains("编程语言与基础", "框架与中间件", "数据库与缓存", "项目与业务", "HR与软素质");
    }

    @Test
    @DisplayName("简历优化：逐条给出原文与改写，没改动的句子和板块标题都不进列表")
    void optimizeResumeRewritesEachLine() throws Exception {
        JsonNode result = reply(AiTask.RESUME_OPTIMIZE,
                Map.of("jd", JD, "resume", RAW_MATERIAL, "focus", "突出高并发经验"));

        JsonNode rewrites = result.path("rewrites");
        assertThat(rewrites).hasSize(2);
        for (JsonNode item : rewrites) {
            assertThat(item.path("section").asText()).isEqualTo("项目经历");
            assertThat(item.path("original").asText()).isNotBlank();
            assertThat(item.path("optimized").asText()).isNotBlank();
            assertThat(item.path("reason").asText()).isNotBlank();
        }
        assertThat(rewrites.findValuesAsText("original"))
                .doesNotContain("项目经历", "技能", "个人简介");
        assertThat(rewrites.findValuesAsText("original"))
                .containsExactly("1. 负责订单系统的重构，引入 Redis 缓存后接口耗时从 800ms 降到 120ms",
                        "2. 参与支付链路的稳定性治理");
    }

    @Test
    @DisplayName("简历优化：技能罗列行只换表达，不加「补量化数据」这种无意义的占位")
    void optimizeResumeDoesNotAskMetricsForSkillLines() throws Exception {
        JsonNode result = reply(AiTask.RESUME_OPTIMIZE,
                Map.of("jd", JD, "resume", "技能\n完成 Java、Redis 的学习与实战"));

        assertThat(result.path("rewrites").findValuesAsText("optimized"))
                .allSatisfy(text -> assertThat(text).doesNotContain("【待补充"));
    }

    @Test
    @DisplayName("简历优化：同行技能标签和带项目符号的教育背景不能误判成项目经历")
    void optimizeResumeDoesNotRewriteStructuredProfileFieldsAsProjects() throws Exception {
        String resume = """
                项目经历
                负责订单系统重构，接口耗时从 800ms 降到 120ms
                技能：Java,Spring Boot,Spring Cloud,MySQL,Redis,RabbitMQ,Docker,Linux,Git
                ▮ 教育背景
                软件测试 广州 随时到岗
                主修课程：Java基础、SpringBoot开发、MySQL数据库、数据结构与算法
                """;

        JsonNode result = reply(AiTask.RESUME_OPTIMIZE, Map.of("jd", JD, "resume", resume));

        assertThat(result.path("rewrites").findValuesAsText("original"))
                .doesNotContain(
                        "技能：Java,Spring Boot,Spring Cloud,MySQL,Redis,RabbitMQ,Docker,Linux,Git",
                        "▮ 教育背景",
                        "软件测试 广州 随时到岗",
                        "主修课程：Java基础、SpringBoot开发、MySQL数据库、数据结构与算法");
        assertThat(result.path("rewrites").findValuesAsText("optimized"))
                .allSatisfy(text -> assertThat(text).doesNotContain("技能：", "主修课程："));
    }

    @Test
    @DisplayName("简历优化：常见项目符号和同行教育标签都能切换到教育板块")
    void optimizeResumeRecognizesCommonEducationHeadingFormats() throws Exception {
        for (String education : List.of(
                "- 教育背景\n软件测试 广州 随时到岗",
                "* 教育背景\n软件测试 广州 随时到岗",
                "· 教育背景\n软件测试 广州 随时到岗",
                "• 教育背景\n软件测试 广州 随时到岗",
                "教育背景：软件测试 广州 随时到岗",
                "教育经历：软件测试 广州 随时到岗")) {
            JsonNode result = reply(AiTask.RESUME_OPTIMIZE,
                    Map.of("jd", JD, "resume", "项目经历\n" + education));

            assertThat(result.path("rewrites").findValuesAsText("optimized"))
                    .as(education)
                    .allSatisfy(text -> assertThat(text).doesNotContain("【待补充：量化结果"));
        }
    }

    @Test
    @DisplayName("简历优化：PDF 后置标题下的实习内容和技能必须归到正确板块")
    void optimizeResumeClassifiesTrailingPdfSections() throws Exception {
        String resume = "技能：Java,Spring Boot,MySQL,Redis,Python,自动化测试,JMeter,SQL\n"
                + new String(Objects.requireNonNull(
                getClass().getResourceAsStream("/resume-sample-pdf.txt")).readAllBytes(), StandardCharsets.UTF_8);

        JsonNode result = reply(AiTask.RESUME_OPTIMIZE, Map.of("jd", JD, "resume", resume));

        assertThat(sectionOf(result.path("rewrites"), "1. 负责 Web 端与移动端产品的功能测试"))
                .isEqualTo("工作经历");
        assertThat(sectionOf(result.path("rewrites"), "1. 熟悉UI自动化测试"))
                .isEqualTo("技能");
    }

    @Test
    @DisplayName("简历优化：素材本身没毛病时如实说明，而不是硬凑几条改写")
    void optimizeResumeSaysNothingToFix() throws Exception {
        JsonNode result = reply(AiTask.RESUME_OPTIMIZE,
                Map.of("jd", JD, "resume", "项目经历\n主导订单系统重构，接口耗时从 800ms 降到 120ms"));

        assertThat(result.path("rewrites")).isEmpty();
        assertThat(result.path("comment").asText()).contains("没有识别到需要改写的表达");
    }

    @Test
    @DisplayName("简历优化：缺量化数据时给占位提示，不替候选人编数字")
    void optimizeResumeFlagsMissingMetricsInsteadOfInventingThem() throws Exception {
        JsonNode result = reply(AiTask.RESUME_OPTIMIZE,
                Map.of("jd", JD, "resume", "项目经历\n2. 参与支付链路的稳定性治理"));

        JsonNode rewrite = result.path("rewrites").get(0);
        assertThat(rewrite.path("original").asText()).isEqualTo("2. 参与支付链路的稳定性治理");
        assertThat(rewrite.path("optimized").asText())
                .startsWith("深度参与")
                .contains("【待补充");
        assertThat(rewrite.path("reason").asText()).contains("偏弱");
    }

    @Test
    @DisplayName("简历优化：有量化数据的条目不再追加占位提示")
    void optimizeResumeKeepsQuantifiedLines() throws Exception {
        JsonNode result = reply(AiTask.RESUME_OPTIMIZE,
                Map.of("jd", JD, "resume", "负责订单系统重构，接口耗时从 800ms 降到 120ms"));

        String optimized = result.path("rewrites").get(0).path("optimized").asText();
        assertThat(optimized).startsWith("主导").doesNotContain("【待补充");
    }

    @Test
    @DisplayName("简历优化：输出关键词覆盖、完整优化稿和待补充清单")
    void optimizeResumeReportsKeywordCoverage() throws Exception {
        JsonNode result = reply(AiTask.RESUME_OPTIMIZE, Map.of("jd", JD, "resume", RAW_MATERIAL));

        assertThat(toStringList(result.path("matchedKeywords"))).contains("Java", "Spring Boot", "MySQL", "Redis");
        assertThat(toStringList(result.path("missingKeywords"))).contains("Kafka");
        assertThat(toStringList(result.path("suggestions"))).isNotEmpty();

        String content = result.path("optimizedContent").asText();
        assertThat(content).contains("技能：").contains("项目经历：").contains("【待补充】");
        assertThat(result.path("comment").asText()).contains("本地模拟引擎");
    }

    @Test
    @DisplayName("简历优化：素材太短时不硬凑改写，直接提示补内容")
    void optimizeResumeWithTooShortMaterial() throws Exception {
        JsonNode result = reply(AiTask.RESUME_OPTIMIZE, Map.of("jd", JD, "resume", "技能\nJava"));

        assertThat(result.path("rewrites")).isEmpty();
        assertThat(result.path("comment").asText()).contains("没有识别到可以改写的经历描述");
    }

    private static List<String> toStringList(JsonNode array) {
        List<String> values = new java.util.ArrayList<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
    }

    private static String sectionOf(JsonNode rewrites, String originalPrefix) {
        for (JsonNode rewrite : rewrites) {
            if (rewrite.path("original").asText().startsWith(originalPrefix)) {
                return rewrite.path("section").asText();
            }
        }
        return null;
    }
}
