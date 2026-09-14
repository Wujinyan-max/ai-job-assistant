package com.jobassistant.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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
                "categories", List.of("Java基础", "Redis"),
                "count", 2,
                "difficulty", "HARD"));

        JsonNode questions = result.path("questions");
        assertThat(questions).hasSize(4);
        for (JsonNode question : questions) {
            assertThat(question.path("question").asText()).isNotBlank();
            assertThat(question.path("answer").asText()).isNotBlank();
            assertThat(question.path("category").asText()).isIn("Java基础", "Redis");
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
        assertThat(categories).contains("Java基础", "Spring Boot", "MySQL", "Redis", "项目");
    }

    private static List<String> toStringList(JsonNode array) {
        List<String> values = new java.util.ArrayList<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
    }
}
