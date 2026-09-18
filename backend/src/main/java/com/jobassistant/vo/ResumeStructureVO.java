package com.jobassistant.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 结构化简历，排版导出 PDF 的数据源。
 * <p>resume.content 是一段纯文本（PDF 导入的结果还可能把标题挤到正文后面），
 * 没法直接排版，所以先用 AI（未配置 API Key 时退化成 {@code MockAiEngine} 的规则解析）
 * 把它识别成这个固定结构，再交给前端渲染成 A4 分页简历。</p>
 */
@Schema(description = "结构化简历，排版导出 PDF 的数据源")
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResumeStructureVO(
        @Schema(description = "基本信息")
        Basics basics,

        @Schema(description = "教育经历")
        List<Education> education,

        @Schema(description = "工作 / 实习经历")
        List<Work> work,

        @Schema(description = "项目经历")
        List<Project> projects,

        @Schema(description = "专业技能，每条是一个技能点")
        List<String> skills,

        @Schema(description = "荣誉证书 / 获奖经历")
        List<String> honors
) {

    public ResumeStructureVO {
        basics = basics == null ? Basics.empty() : basics;
        education = education == null ? List.of() : education;
        work = work == null ? List.of() : work;
        projects = projects == null ? List.of() : projects;
        skills = skills == null ? List.of() : skills;
        honors = honors == null ? List.of() : honors;
    }

    /** 一份完全空的结构，用于模型什么都没识别出来时兜底 */
    public static ResumeStructureVO empty() {
        return new ResumeStructureVO(null, null, null, null, null, null);
    }

    @Schema(description = "基本信息")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Basics(
            String name,
            @Schema(description = "求职意向 / 职位标题，如「软件测试工程师」")
            String label,
            String phone,
            String email,
            @Schema(description = "期望城市 / 现居城市")
            String city,
            @Schema(description = "工作年限，如「应届生」「5 年」")
            String workYears,
            @Schema(description = "个人简介 / 自我评价")
            String summary
    ) {
        public static Basics empty() {
            return new Basics(null, null, null, null, null, null, null);
        }
    }

    @Schema(description = "教育经历")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Education(
            @Schema(description = "学校名称")
            String school,
            @Schema(description = "专业")
            String major,
            @Schema(description = "学历，如 本科 / 硕士")
            String degree,
            @Schema(description = "起止时间，如 2019.09 - 2023.06")
            String period,
            @Schema(description = "在校经历、GPA、主修课程等补充说明")
            String detail
    ) {
    }

    @Schema(description = "工作 / 实习经历")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Work(
            String company,
            @Schema(description = "职位")
            String position,
            String period,
            @Schema(description = "工作内容，每条一句话")
            List<String> bullets
    ) {
        public Work {
            bullets = bullets == null ? List.of() : bullets;
        }
    }

    @Schema(description = "项目经历")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Project(
            @Schema(description = "项目名称")
            String name,
            @Schema(description = "担任角色")
            String role,
            String period,
            @Schema(description = "项目描述 / 技术架构")
            String summary,
            @Schema(description = "项目职责与成果，每条一句话")
            List<String> bullets
    ) {
        public Project {
            bullets = bullets == null ? List.of() : bullets;
        }
    }
}
