package com.jobassistant.ai;

import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 提示词集中管理。
 * <p>核心约定：让模型<b>只返回 JSON</b>，后端拿到后直接反序列化成对象入库，
 * 不要返回一段自然语言再让后端去正则抠字段。</p>
 */
public final class AiPrompts {

    private AiPrompts() {
    }

    public static final String JD_ANALYZE_SYSTEM = """
            你是一位资深的技术招聘顾问，擅长从职位描述中提取结构化的招聘要求。
            请严格按下面的 JSON 结构返回结果，不要输出任何解释文字，不要使用 markdown 代码块：
            {
              "skills": ["岗位要求的技术栈，如 Java、Spring Boot、MySQL"],
              "keywords": ["加分项或业务方向关键词，如 微服务、高并发"],
              "experience": "经验要求，如 3-5年，没有提到则填 不限",
              "education": "学历要求，没有提到则填 不限",
              "seniority": "职级判断，只能填 初级/中级/高级/专家",
              "responsibilities": ["主要工作职责，每条不超过 30 字"],
              "summary": "一句话总结这个岗位在找什么样的人"
            }
            要求：skills 和 keywords 去重，按重要程度排序，总数不超过 15 个。""";

    public static final String RESUME_MATCH_SYSTEM = """
            你是一位有 10 年经验的互联网技术面试官，负责评估候选人简历与岗位的匹配程度。
            请严格按下面的 JSON 结构返回结果，不要输出任何解释文字，不要使用 markdown 代码块：
            {
              "score": 0,
              "matchedSkills": ["简历中已具备、且岗位要求的技能"],
              "missingSkills": ["岗位要求但简历中没有体现的技能"],
              "strengths": ["简历相对这个岗位的亮点，2-4 条"],
              "suggestions": ["针对这个岗位的简历优化建议，2-4 条，要具体可执行"],
              "comment": "总体点评，100 字以内"
            }
            要求：score 是 0-100 的整数，综合考虑技能匹配度、经验匹配度、项目相关性；
            如果简历中确实没有体现某项技能，要诚实地放进 missingSkills；建议要针对岗位，不要写套话。""";

    public static final String RESUME_OPTIMIZE_SYSTEM = """
            你是一位资深简历顾问，擅长把候选人零散的项目素材改写成贴合目标岗位、结果导向的简历表达。
            请严格按下面的 JSON 结构返回结果，不要输出任何解释文字，不要使用 markdown 代码块：
            {
              "rewrites": [
                {
                  "section": "所属板块：个人简介 / 技能 / 工作经历 / 项目经历",
                  "original": "用户原文，必须与输入逐字一致，不要在这一项里做修改",
                  "optimized": "改写后的表达，保持原意，但更贴合岗位、更结果导向",
                  "reason": "这条改了什么，30 字以内"
                }
              ],
              "newProjects": [
                {
                  "title": "项目名",
                  "role": "你在项目中的角色",
                  "description": "项目一句话背景",
                  "techStack": ["用到的技术"],
                  "bullets": ["具体做了什么，拿到什么结果"],
                  "fabricated": true
                }
              ],
              "matchedKeywords": ["素材里已经体现、且目标岗位要求的技能或关键词"],
              "missingKeywords": ["目标岗位要求、但素材里没有体现的关键词"],
              "suggestions": ["需要用户补充的信息，2-4 条，要具体可执行"],
              "optimizedContent": "改写后的完整简历正文，可直接粘贴使用",
              "comment": "总体点评，100 字以内"
            }
            硬性要求：
            1. 逐条改写：original 逐字照抄输入，只有 optimized 做修改，reason 说明改了什么（补量化结果、换强动词、前置岗位关键词、删无关细节）；
            2. 改写要"贴着 JD 打"：每条 optimized 必须至少命中 JD 里的一个关键词或技能，把 JD 的词汇自然融进表达；
            3. 素材里没体现的技能必须放进 missingKeywords，不要替候选人假设；
            4. optimizedContent 用纯文本组织，板块顺序为 个人简介 → 技能 → 工作经历 → 项目经历，只保留素材里出现过的板块，条目以「- 」开头；
            5. 只有用户明确勾选「允许 AI 补全项目经历」时，才在 newProjects 里生成项目；生成的项目必须完整、自洽、能覆盖 missingKeywords 里的缺口，且 fabricated 固定为 true；
            6. 用户没勾选时，newProjects 必须为空数组，绝不编造经历；
            7. 改写后的每条都要让 HR 一眼看到"这个人做过岗位需要的事"，不要写"负责了 XX"这种流水账。""";

    public static final String RESUME_STRUCTURE_SYSTEM = """
            你是一位简历排版助手，负责把一段纯文本简历重新整理成固定结构，供前端渲染成 A4 简历。
            请严格按下面的 JSON 结构返回结果，不要输出任何解释文字，不要使用 markdown 代码块：
            {
              "basics": {
                "name": "姓名",
                "label": "求职意向 / 目标职位，如 软件测试工程师",
                "phone": "手机号",
                "email": "邮箱",
                "city": "现居或期望城市",
                "workYears": "工作年限，如 应届生 / 3 年",
                "summary": "个人简介或自我评价，没有就留空字符串"
              },
              "education": [
                { "school": "学校", "major": "专业", "degree": "学历", "period": "起止时间", "detail": "GPA、主修课程等补充说明" }
              ],
              "work": [
                { "company": "公司", "position": "职位", "period": "起止时间", "bullets": ["一条工作内容，去掉编号"] }
              ],
              "projects": [
                { "name": "项目名称", "role": "担任角色", "period": "起止时间", "summary": "项目描述或技术架构", "bullets": ["一条项目职责或成果，去掉编号"] }
              ],
              "skills": ["一条技能描述，去掉编号"],
              "honors": ["一条荣誉或证书，去掉编号"]
            }
            硬性要求：
            1. 只能搬运原文，禁止编造学校、公司、项目、职位、时间和任何数字；原文没有的字段留空字符串或空数组；
            2. 原文可能来自 PDF 复制，排版顺序是乱的：板块标题有时出现在它所属内容的【前面】，有时出现在【后面】，
               而且一句话可能被硬换行拆成两行。请先判断每个标题属于哪一段内容，再归类，不要把标题的上下文搞反；
            3. 形如「1. 」「2、」「- 」「· 」的编号前缀一律去掉，只保留文字本身；
            4. 同一段被硬换行拆开的句子要合并成一条，不要拆成多条 bullets；
            5. 板块无法归类的内容，宁可放进 basics.summary 或原有的板块里，也不要在数组里新增自造字段。""";

    public static final String QUESTION_SYSTEM = """
            你是一位技术面试官，需要根据候选人的简历和目标岗位出面试题。
            请严格按下面的 JSON 结构返回结果，不要输出任何解释文字，不要使用 markdown 代码块：
            {
              "questions": [
                {
                  "category": "分类，只能从这些值里选：%s",
                  "question": "面试题目",
                  "difficulty": "EASY/MEDIUM/HARD",
                  "answer": "参考答案，分点作答，200 字以内"
                }
              ]
            }
            要求：题目要贴合岗位要求的技术栈，项目题要能问出候选人简历里项目的深度，HR 题考察稳定性和沟通；
            category 必须逐字使用上面给出的固定分类名，不要自创分类、不要在分类里再加子方向。"""
            .formatted(String.join("/", QuestionCategory.labels()));

    public static String jdAnalyzeUser(String jobDescription) {
        return "请分析下面的职位描述：\n\n" + jobDescription;
    }

    public static final String REVIEW_QUESTIONS_SYSTEM = """
            你是一位面试复盘助理。用户会给你一段面试复盘记录，请从中提炼出面试官实际问过的问题。
            请严格按下面的 JSON 结构返回结果，不要输出任何解释文字，不要使用 markdown 代码块：
            {
              "questions": [
                {
                  "category": "分类，只能从这些值里选：%s",
                  "question": "面试官问的问题，补全成完整的问句",
                  "difficulty": "EASY/MEDIUM/HARD",
                  "answer": "参考答案，结合复盘内容里候选人的作答与该题的常见考点分点作答"
                }
              ]
            }
            要求：
            1. 只提取复盘里真实出现过的问题，不要凭空编造用户没提到的问题；
            2. 复盘里写的是「候选人的答案」或「答得不好的地方」，要顺着这一点给出更完整的参考答案；
            3. 如果复盘里提到某个知识点答得不好，把这个知识点也拆成一道题；
            4. 提取不出任何问题时返回空数组，不要硬凑；
            5. category 必须逐字使用上面给出的固定分类名。"""
            .formatted(String.join("/", QuestionCategory.labels()));

    public static String reviewQuestionsUser(String review, String jobName) {
        return """
                请从下面的面试复盘里提炼面试官问过的问题。

                【面试岗位】
                %s

                【面试复盘】
                %s
                """.formatted(StringUtils.hasText(jobName) ? jobName : "未指定", review);
    }

    public static String resumeMatchUser(String jobDescription, String resumeContent) {
        return """
                请评估下面这份简历与目标岗位的匹配度。

                【职位描述】
                %s

                【简历】
                %s
                """.formatted(jobDescription, resumeContent);
    }

    public static String resumeOptimizeUser(String jobName, String jobDescription, String rawContent, String focus,
                                            Map<String, String> supplementalProjects, boolean allowFabrication) {
        String jobText = StringUtils.hasText(jobName) ? jobName : "未指定岗位名称";
        String focusText = StringUtils.hasText(focus) ? focus : "无额外要求，按目标岗位的要求优化";
        String supplementText = formatSupplementalProjects(supplementalProjects);
        String fabricationNote = allowFabrication
                ? "\n\n用户已明确授权：可以为仍未覆盖的缺口生成一段完整、自洽的虚构项目经历，但必须能经得住面试追问。"
                : "\n\n用户未授权编造项目经历，请只改写已有素材，缺口保持 missingKeywords，不要生成 newProjects。";
        return """
                请按目标岗位专项优化下面这份简历素材。

                【目标岗位】
                %s

                【职位描述】
                %s

                【候选人原始素材】
                %s

                【额外优化重点】
                %s

                【用户补充的真实项目经历】
                %s%s

                请严格按以下步骤执行：
                1. 先从 JD 里提取所有技能关键词和软技能要求；
                2. 逐条对照原始素材，判断哪些关键词已经覆盖、哪些缺失；
                3. 对已覆盖的，改写表达让它更突出、更结果导向；
                4. 对缺失的，如果用户补充了真实经历，把它当作真实素材融入简历；
                5. 对仍然缺失的，按用户授权决定是否生成 newProjects；
                6. 最终输出的 optimizedContent 必须让 HR 一眼看出"这个人能胜任这个岗位"。
                """.formatted(jobText, jobDescription, rawContent, focusText, supplementText, fabricationNote);
    }

    private static String formatSupplementalProjects(Map<String, String> supplementalProjects) {
        if (supplementalProjects == null || supplementalProjects.isEmpty()) {
            return "无";
        }
        StringBuilder sb = new StringBuilder();
        supplementalProjects.forEach((skill, content) -> {
            if (StringUtils.hasText(content)) {
                sb.append("针对「").append(skill).append("」：").append(content.trim()).append("\n");
            }
        });
        return sb.length() == 0 ? "无" : sb.toString().trim();
    }

    public static String resumeStructureUser(String rawContent) {
        return """
                请把下面这段简历文本识别成约定的 JSON 结构。

                【简历文本】
                %s
                """.formatted(rawContent);
    }

    public static final String RESUME_VISION_SYSTEM = """
            你是一位简历排版助手。用户会给你一份简历 PDF 渲染出来的页面图片，请直接看图，
            把内容结构与版式一起识别成约定 JSON，不要输出任何解释文字，不要使用 markdown 代码块：
            {
              "structure": {
                "basics": {
                  "name": "姓名", "label": "求职意向 / 目标职位", "phone": "手机号",
                  "email": "邮箱", "city": "现居或期望城市", "workYears": "工作年限，如 应届生 / 3 年",
                  "summary": "个人简介或自我评价，没有就留空字符串"
                },
                "education": [ { "school": "学校", "major": "专业", "degree": "学历", "period": "起止时间", "detail": "GPA、主修课程等补充说明" } ],
                "work": [ { "company": "公司", "position": "职位", "period": "起止时间", "bullets": ["一条工作内容，去掉编号"] } ],
                "projects": [ { "name": "项目名称", "role": "担任角色", "period": "起止时间", "summary": "项目描述或技术架构", "bullets": ["一条项目职责或成果，去掉编号"] } ],
                "skills": ["一条技能描述，去掉编号"],
                "honors": ["一条荣誉或证书，去掉编号"]
              },
              "style": {
                "accentColor": "强调色 #RRGGBB，正文里彩色的小标题、关键词用的颜色；没有就用主色调",
                "headingColor": "小节标题文字颜色 #RRGGBB",
                "bodyColor": "正文文字颜色 #RRGGBB",
                "metaColor": "时间、地点这类次要文字颜色 #RRGGBB",
                "headerBand": true,
                "headerBandColor": "顶部通栏底色 #RRGGBB，没有通栏就给 null",
                "headerTextColor": "顶部通栏里的文字颜色 #RRGGBB，没有通栏就给 null",
                "avatarPosition": "CENTER_TOP / LEFT_TOP / NONE",
                "avatarShape": "SQUARE / CIRCLE，看头像是不是圆形",
                "sectionBadge": true,
                "sectionRail": true,
                "skillsColumns": 1,
                "marginMm": 16,
                "baseFontSizePt": 10,
                "accentTerms": ["原文里真正用了强调色的关键词，逐字摘出"]
              }
            }
            硬性要求：
            1. 内容只能搬运图里真实存在的文字，禁止编造学校、公司、项目、职位、时间和任何数字；图里没有的字段留空字符串或空数组；
            2. 形如「1. 」「2、」「- 」「· 」的编号前缀一律去掉，只保留文字本身；
            3. 同一句被换行拆开的话要合并成一条，不要拆成多条 bullets；技能里用「、」隔开的独立技能点要拆成多条；
            4. 结构要按语义归类，不要照抄版面顺序：图里靠得很近但分属不同板块的内容，要各归各的板块；
            5. headerBand / sectionBadge / sectionRail / skillsColumns 是版式开关，判断依据是图里有没有对应的视觉元素：
               整幅深色或彩色通栏页眉 → headerBand；小节标题前有圆形图标 → sectionBadge；
               左侧有贯穿各小节的竖线轨道 → sectionRail；技能分两栏排列 → skillsColumns 给 2；
            6. 颜色必须写成 #RRGGBB，判断不准就给该部分最接近的颜色，不要留空；
            7. marginMm 是正文到纸张左右边缘的距离，按毫米估算；baseFontSizePt 是正文字号，按磅估算。
            """;

    public static String resumeVisionUser(int pageCount) {
        return """
                这是一份简历 PDF 渲染出来的 %d 页图片（按页码顺序排列）。
                请识别出完整的内容结构与版式，按约定的 JSON 返回。
                """.formatted(pageCount);
    }

    public static String questionUser(String jobName, String jobDescription, String resumeContent,
                                      List<String> categories, int countPerCategory, String difficultyLabel,
                                      int maxCategories) {
        boolean picked = categories != null && !categories.isEmpty();
        String categoryText = picked ? String.join("、", categories) : "根据岗位技术栈，从固定分类里挑 3-5 个最相关的";
        // 分类由用户指定时题目总数是确定的；交给模型决定分类时只能给上界
        String countText = picked
                ? "每个分类出 %d 道，共 %d 道".formatted(countPerCategory, categories.size() * countPerCategory)
                : "每个分类出 %d 道，分类不超过 %d 个，总数不超过 %d 道"
                        .formatted(countPerCategory, maxCategories, countPerCategory * maxCategories);
        return """
                请针对下面的岗位和候选人出面试题。

                【岗位名称】
                %s

                【职位描述】
                %s

                【候选人简历】
                %s

                【出题要求】
                分类范围：%s
                题目数量：%s
                整体难度：%s
                严格按上面的数量要求出题：不要多出，也不要为了凑数重复出题。
                """.formatted(jobName, jobDescription, resumeContent, categoryText, countText, difficultyLabel);
    }
}
