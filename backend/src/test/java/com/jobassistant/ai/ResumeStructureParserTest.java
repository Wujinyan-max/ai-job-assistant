package com.jobassistant.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 简历结构化：PDF 复制出来的简历里，板块标题经常被挤到所属内容的【后面】，
 * 没配 API Key 时走的就是这个规则解析器，它认不出来「结构化简历」就直接没法用。
 */
class ResumeStructureParserTest {

    /** 真实简历（PDF 导入）的排版：标题都排在所属内容的后面 */
    private static final String PDF_RESUME = """
            吴锦炎
            软件测试 广州 随时到岗
            22岁 男 汉族 广东 应届生 13266279142 2842836490@qq.com
            2022-09 ~ 2026-07 广东科技学院 软件工程（本科）
            专业成绩：GPA 3.66/4 （专业前5%）
             教育背景
            2025-09 ~ 2026-01 百度 软件测试实习生
            1. 负责 Web 端产品的功能测试，独立设计并执行核心模块测试用例，排
            查功能缺陷并跟进 Bug 闭环。
            2. 参与每日版本构建后的 BVT 集成测试。
             工作经验
            1. 熟悉UI自动化测试，能独立搭建基于 Python+Selenium 的整套框架
             技能特长
            2025-10 ~ 2025-12 智慧云课堂教育管理平台 软件测试实习生
            项目架构： 基于 Spring Boot 微服务架构，MySQL/Redis 数据存储
            工作内容：
            1. 全流程功能测试：独立负责课程中心与作业管理模块
             项目经验
            1. 软件设计师中级证书
            2. 第七届传智杯全国IT大赛一等奖
             荣誉证书
            1. 我做过四个月的软件测试实习工作
             自我评价
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MockAiEngine engine = new MockAiEngine(objectMapper);

    private JsonNode structure(String resume) throws Exception {
        return objectMapper.readTree(engine.reply(new AiRequest(
                AiTask.RESUME_STRUCTURE, "system", "user", Map.of("resume", resume))));
    }

    private static List<String> toStringList(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
    }

    @Test
    @DisplayName("结构化：标题排在正文后面的 PDF 排版也能各归各位")
    void structureWithTrailingTitles() throws Exception {
        JsonNode result = structure(PDF_RESUME);

        JsonNode basics = result.path("basics");
        assertThat(basics.path("name").asText()).isEqualTo("吴锦炎");
        assertThat(basics.path("label").asText()).isEqualTo("软件测试");
        assertThat(basics.path("city").asText()).isEqualTo("广州");
        assertThat(basics.path("workYears").asText()).isEqualTo("应届生");
        assertThat(basics.path("phone").asText()).isEqualTo("13266279142");
        assertThat(basics.path("email").asText()).isEqualTo("2842836490@qq.com");

        JsonNode education = result.path("education").path(0);
        assertThat(education.path("school").asText()).isEqualTo("广东科技学院");
        assertThat(education.path("major").asText()).isEqualTo("软件工程");
        assertThat(education.path("degree").asText()).isEqualTo("本科");
        assertThat(education.path("period").asText()).isEqualTo("2022-09~2026-07");
        assertThat(education.path("detail").asText()).contains("GPA 3.66/4");
    }

    @Test
    @DisplayName("结构化：工作经历取到公司职位，技能和荣誉不会串到别的板块")
    void structureKeepsSectionsApart() throws Exception {
        JsonNode result = structure(PDF_RESUME);

        JsonNode work = result.path("work").path(0);
        assertThat(work.path("company").asText()).isEqualTo("百度");
        assertThat(work.path("position").asText()).isEqualTo("软件测试实习生");
        assertThat(work.path("period").asText()).isEqualTo("2025-09~2026-01");
        assertThat(toStringList(work.path("bullets"))).hasSize(2);

        JsonNode project = result.path("projects").path(0);
        assertThat(project.path("name").asText()).isEqualTo("智慧云课堂教育管理平台");
        assertThat(project.path("role").asText()).isEqualTo("软件测试实习生");
        assertThat(project.path("summary").asText()).contains("Spring Boot 微服务架构");
        assertThat(toStringList(project.path("bullets"))).hasSize(1);

        assertThat(toStringList(result.path("skills")).get(0)).startsWith("熟悉UI自动化测试");
        assertThat(toStringList(result.path("honors")))
                .containsExactly("软件设计师中级证书", "第七届传智杯全国IT大赛一等奖");
        assertThat(result.path("basics").path("summary").asText()).contains("四个月的软件测试实习工作");
    }

    @Test
    @DisplayName("结构化：硬换行合并成一条，编号前缀去掉")
    void structureMergesHardWrappedLines() throws Exception {
        JsonNode result = structure(PDF_RESUME);

        List<String> bullets = toStringList(result.path("work").path(0).path("bullets"));
        assertThat(bullets.get(0)).doesNotContain("1. ");
        assertThat(bullets.get(0)).startsWith("负责 Web 端产品的功能测试");
        // 原文被切成「排」+「查功能缺陷...」两行，合并后应该是一整句
        assertThat(bullets.get(0)).contains("，排查功能缺陷并跟进 Bug 闭环。");
        assertThat(bullets.get(0)).doesNotContain("\n");
        assertThat(bullets.get(1)).isEqualTo("参与每日版本构建后的 BVT 集成测试。");
    }

    @Test
    @DisplayName("结构化：标题在正文前面的常规排版同样识别")
    void structureWithLeadingTitles() throws Exception {
        JsonNode result = structure("""
                张伟
                男 | 5年经验 | 13812345678 | zhangwei@example.com
                教育背景
                某某大学 本科
                专业技能
                Java、Spring Boot、MySQL、Redis、Kafka
                """);

        assertThat(result.path("basics").path("name").asText()).isEqualTo("张伟");
        assertThat(result.path("basics").path("workYears").asText()).isEqualTo("5年经验");
        assertThat(result.path("education").path(0).path("school").asText()).isEqualTo("某某大学");
        assertThat(result.path("education").path(0).path("degree").asText()).isEqualTo("本科");
        assertThat(toStringList(result.path("skills")))
                .containsExactly("Java", "Spring Boot", "MySQL", "Redis", "Kafka");
    }

    @Test
    @DisplayName("结构化：原文没有的字段留空，不替用户编造")
    void structureDoesNotInventMissingFields() throws Exception {
        JsonNode result = structure("""
                张伟
                男 | 13812345678
                教育背景
                某某大学
                """);

        JsonNode education = result.path("education").path(0);
        assertThat(education.path("major").isNull()).isTrue();
        assertThat(education.path("period").isNull()).isTrue();
        assertThat(education.path("detail").isNull()).isTrue();
        assertThat(result.path("basics").path("label").isNull()).isTrue();
        assertThat(result.path("basics").path("summary").isNull()).isTrue();
        assertThat(result.path("work")).isEmpty();
        assertThat(result.path("projects")).isEmpty();
        assertThat(result.path("skills")).isEmpty();
    }

    @Test
    @DisplayName("结构化：标题和正文挤在一行的写法也能识别")
    void structureWithInlineTitle() throws Exception {
        JsonNode result = structure("""
                项目经历：1. 交易系统重构，负责订单模块，引入 Redis 缓存后接口耗时从 800ms 降到 120ms；
                2. 使用 Spring Boot + MyBatis 搭建对账服务。
                """);

        JsonNode project = result.path("projects").path(0);
        assertThat(project.path("name").asText()).isEqualTo("交易系统重构");
        List<String> bullets = toStringList(project.path("bullets"));
        assertThat(bullets).hasSize(2);
        assertThat(bullets.get(0)).startsWith("负责订单模块");
        assertThat(bullets.get(1)).startsWith("使用 Spring Boot + MyBatis");
    }
    @Test
    @DisplayName("结构化：真实 PDF 简历（标题排在正文后面）端到端回归")
    void structureRealPdfResume() throws Exception {
        String resume = new String(Objects.requireNonNull(
                getClass().getResourceAsStream("/resume-sample-pdf.txt")).readAllBytes(), StandardCharsets.UTF_8);

        JsonNode result = structure(resume);
        JsonNode basics = result.path("basics");
        assertThat(basics.path("name").asText()).isEqualTo("吴锦炎");
        assertThat(basics.path("label").asText()).isEqualTo("软件测试");
        assertThat(basics.path("city").asText()).isEqualTo("广州");
        assertThat(basics.path("workYears").asText()).isEqualTo("应届生");
        assertThat(basics.path("phone").asText()).isEqualTo("13266279142");
        assertThat(basics.path("email").asText()).isEqualTo("2842836490@qq.com");

        assertThat(result.path("education")).hasSize(1);
        assertThat(result.path("education").path(0).path("school").asText()).isEqualTo("广东科技学院");
        assertThat(result.path("education").path(0).path("major").asText()).isEqualTo("软件工程");
        assertThat(result.path("education").path(0).path("detail").asText()).contains("GPA 3.66/4");

        assertThat(result.path("work")).hasSize(1);
        assertThat(result.path("work").path(0).path("company").asText()).isEqualTo("百度");
        assertThat(result.path("work").path(0).path("bullets")).hasSize(5);

        assertThat(result.path("skills")).hasSize(7);
        assertThat(toStringList(result.path("skills")).get(0)).startsWith("熟悉UI自动化测试");

        assertThat(result.path("projects")).hasSize(2);
        assertThat(result.path("projects").path(0).path("name").asText()).isEqualTo("安享智慧理财");
        assertThat(result.path("projects").path(1).path("name").asText()).isEqualTo("智慧云课堂教育管理平台");
        assertThat(result.path("projects").path(0).path("bullets")).hasSize(6);
        assertThat(result.path("projects").path(0).path("summary").asText()).contains("核心账务系统");
        assertThat(result.path("projects").path(1).path("bullets")).hasSize(4);
        assertThat(result.path("projects").path(1).path("summary").asText()).contains("教务系统");

        assertThat(result.path("honors")).hasSize(5);
        assertThat(toStringList(result.path("honors")).get(0)).isEqualTo("软件设计师中级证书");
        assertThat(basics.path("summary").asText())
                .contains("我做过四个月的软件测试实习工作")
                .contains("在校期间，获得过多个软件测试相关的奖项");
    }
}