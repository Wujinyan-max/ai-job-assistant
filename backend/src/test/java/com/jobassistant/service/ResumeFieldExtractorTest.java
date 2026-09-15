package com.jobassistant.service;

import com.jobassistant.service.impl.ResumeFieldExtractor;
import com.jobassistant.service.impl.ResumeFieldExtractor.ParsedFields;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Year;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「导入简历」能自动填对多少字段，取决于这里的抽取规则，用一份典型中文简历把行为固定下来。
 */
class ResumeFieldExtractorTest {

    private static final String SAMPLE = """
            张伟
            男 | 5年经验 | 13812345678 | zhangwei@example.com

            求职意向：Java 后端开发工程师

            教育背景
            2015.09 - 2019.06  某某大学  计算机科学与技术  本科

            工作经历
            2019.07 - 至今  某某科技有限公司  Java 后端开发
            负责订单系统的架构设计与性能优化，用 Spring Boot、MySQL、Redis、Kafka 搭起微服务。

            专业技能
            熟悉 Java、Spring Boot、MyBatis-Plus、MySQL、Redis、Kafka、Docker、Kubernetes；
            了解 JavaScript 与 Vue3。

            自我评价
            五年后端开发经验，做过高并发场景的性能优化，擅长把复杂问题拆成小步骤。
            """;

    @Test
    @DisplayName("典型中文简历能抽出姓名、联系方式、学历、年限、求职意向和简介")
    void extractsCommonFields() {
        ParsedFields fields = ResumeFieldExtractor.extract(ResumeFieldExtractor.normalize(SAMPLE));

        assertThat(fields.name()).isEqualTo("张伟");
        assertThat(fields.phone()).isEqualTo("13812345678");
        assertThat(fields.email()).isEqualTo("zhangwei@example.com");
        assertThat(fields.education()).isEqualTo("本科");
        assertThat(fields.workYears()).isEqualTo(5);
        assertThat(fields.intent()).isEqualTo("Java 后端开发工程师");
        assertThat(fields.summary()).contains("高并发").doesNotContain("自我评价");
    }

    @Test
    @DisplayName("技能按别名识别，并且卡住英文单词边界")
    void recognisesSkillsWithWordBoundaries() {
        List<String> skills = skillsOf(ResumeFieldExtractor.extract(ResumeFieldExtractor.normalize(SAMPLE)));

        assertThat(skills).contains("Java", "Spring Boot", "MyBatis-Plus", "MySQL", "Redis",
                "Kafka", "Docker", "Kubernetes", "Vue", "JavaScript");
        // MySQL 里的 SQL、MyBatis-Plus 里的 MyBatis 都不该被单独识别成一个技能
        assertThat(skills).doesNotContain("MyBatis", "SQL");
    }

    @Test
    @DisplayName("没写「N 年经验」时，按工作经历的起始年份推算年限")
    void infersWorkYearsFromEarliestWorkStart() {
        int startYear = Year.now().getValue() - 4;

        ParsedFields fields = ResumeFieldExtractor.extract(
                "姓名：李娜\n\n工作经历\n" + startYear + ".03 - 至今  某某公司  测试开发工程师\n");

        assertThat(fields.name()).isEqualTo("李娜");
        assertThat(fields.workYears()).isEqualTo(4);
    }

    @Test
    @DisplayName("「姓名」「电话」这类标签独占一行时，不会被当成姓名")
    void labelledLinesAreNotTreatedAsName() {
        ParsedFields fields = ResumeFieldExtractor.extract("""
                姓名
                张伟
                电话
                13812345678
                邮箱
                zhangwei@example.com
                技能
                Java、MySQL
                """);

        assertThat(fields.name()).isEqualTo("张伟");
        assertThat(fields.phone()).isEqualTo("13812345678");
        assertThat(fields.email()).isEqualTo("zhangwei@example.com");
        assertThat(skillsOf(fields)).contains("Java", "MySQL");
    }

    @Test
    @DisplayName("空文本不瞎猜，字段全部留空交给用户补")
    void blankTextExtractsNothing() {
        ParsedFields fields = ResumeFieldExtractor.extract("   \n\n   ");

        assertThat(fields.name()).isNull();
        assertThat(fields.phone()).isNull();
        assertThat(fields.email()).isNull();
        assertThat(fields.education()).isNull();
        assertThat(fields.workYears()).isNull();
        assertThat(fields.skills()).isNull();
        assertThat(fields.summary()).isNull();
    }

    @Test
    @DisplayName("normalize 统一换行、全角空格和多余空行")
    void normalizeCleansUpWhitespace() {
        assertThat(ResumeFieldExtractor.normalize("张伟\r\n\r\n\r\n  \u3000Java  \r\n"))
                .isEqualTo("张伟\n\nJava");
    }

    private static List<String> skillsOf(ParsedFields fields) {
        return fields.skills() == null ? List.of() : List.of(fields.skills().split(","));
    }
}
