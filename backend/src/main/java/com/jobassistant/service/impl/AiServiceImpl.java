package com.jobassistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobassistant.ai.AiClient;
import com.jobassistant.ai.AiPrompts;
import com.jobassistant.ai.AiReply;
import com.jobassistant.ai.AiRequest;
import com.jobassistant.ai.AiRuntimeConfig;
import com.jobassistant.ai.AiTask;
import com.jobassistant.ai.QuestionCategory;
import com.jobassistant.common.AiAnalysisType;
import com.jobassistant.common.BusinessException;
import com.jobassistant.common.ErrorCode;
import com.jobassistant.dto.AiConfigSaveDTO;
import com.jobassistant.dto.AiModelQueryDTO;
import com.jobassistant.dto.GenerateQuestionDTO;
import com.jobassistant.dto.ExtractQuestionsDTO;
import com.jobassistant.dto.JdAnalyzeDTO;
import com.jobassistant.dto.ResumeDTO;
import com.jobassistant.dto.ResumeMatchDTO;
import com.jobassistant.dto.ResumeOptimizeDTO;
import com.jobassistant.dto.ResumeStructureDTO;
import com.jobassistant.entity.AiAnalysis;
import com.jobassistant.entity.InterviewQuestion;
import com.jobassistant.entity.Job;
import com.jobassistant.entity.Resume;
import com.jobassistant.mapper.AiAnalysisMapper;
import com.jobassistant.mapper.InterviewQuestionMapper;
import com.jobassistant.security.SecurityUtils;
import com.jobassistant.service.AiService;
import com.jobassistant.service.AiConfigService;
import com.jobassistant.service.JobService;
import com.jobassistant.service.ResumeService;
import com.jobassistant.vo.AiConfigVO;
import com.jobassistant.vo.AiCallVO;
import com.jobassistant.vo.AiUsageVO;
import com.jobassistant.vo.GeneratedQuestionVO;
import com.jobassistant.vo.InterviewQuestionVO;
import com.jobassistant.vo.JdAnalysisVO;
import com.jobassistant.vo.ResumeMatchVO;
import com.jobassistant.vo.ResumeNewProjectVO;
import com.jobassistant.vo.ResumeOptimizeVO;
import com.jobassistant.vo.ResumeStructureVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {

    private static final int DEFAULT_COUNT_PER_CATEGORY = 3;
    private static final int MAX_COUNT_PER_CATEGORY = 5;
    private static final int MAX_CATEGORIES = 5;
    /** resume.title 是 VARCHAR(100)，自动拼出来的标题要按这个长度截断 */
    private static final int MAX_RESUME_TITLE_LENGTH = 100;

    private final AiClient aiClient;
    private final AiAnalysisMapper analysisMapper;
    private final InterviewQuestionMapper questionMapper;
    private final JobService jobService;
    private final ResumeService resumeService;
    private final ObjectMapper objectMapper;
    private final AiConfigService aiConfigService;

    @Override
    public AiConfigVO config() {
        return aiConfigService.getMaskedConfig();
    }

    @Override
    public AiConfigVO saveConfig(AiConfigSaveDTO dto) {
        return aiConfigService.save(dto);
    }

    @Override
    public String testConfig() {
        var runtime = aiConfigService.runtimeConfig();
        if (runtime.apiKey() == null || runtime.apiKey().isBlank()) {
            throw new BusinessException(ErrorCode.AI_CALL_FAILED, "请先填写并保存 API Key");
        }
        AiReply reply = aiClient.chat(new AiRequest(AiTask.JD_ANALYZE,
                "只输出 JSON。", "返回 {\"ok\":true}。", Map.of()), runtime);
        return "连接成功 · " + reply.model();
    }

    @Override
    public List<String> listModels(AiModelQueryDTO dto) {
        var saved = aiConfigService.runtimeConfig();
        String baseUrl = StringUtils.hasText(dto.baseUrl()) ? dto.baseUrl() : saved.baseUrl();
        String apiKey = StringUtils.hasText(dto.apiKey()) ? dto.apiKey() : saved.apiKey();
        String apiMode = StringUtils.hasText(dto.apiMode()) ? dto.apiMode() : saved.apiMode();
        String provider = StringUtils.hasText(dto.provider()) ? dto.provider() : saved.provider();
        return aiClient.listModels(new AiRuntimeConfig(provider, apiMode, baseUrl, apiKey,
                saved.model(), Math.min(saved.timeoutSeconds(), 30)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiCallVO<JdAnalysisVO> analyzeJd(JdAnalyzeDTO dto) {
        Job job = dto.jobId() == null ? null : jobService.requireOwned(dto.jobId());
        String jd = StringUtils.hasText(dto.jobDescription())
                ? dto.jobDescription()
                : (job == null ? null : job.getJobDescription());
        if (!StringUtils.hasText(jd)) {
            throw new BusinessException(ErrorCode.AI_EMPTY_INPUT, "请先粘贴 JD 或选择一个已保存的职位");
        }

        AiRuntimeConfig runtime = aiConfigService.runtimeConfig();
        AiReply reply = aiClient.chat(new AiRequest(
                AiTask.JD_ANALYZE,
                AiPrompts.JD_ANALYZE_SYSTEM,
                AiPrompts.jdAnalyzeUser(jd),
                Map.of("jd", jd)), runtime);
        JdAnalysisVO vo = aiClient.parse(reply.content(), JdAnalysisVO.class);
        AiUsageVO usage = AiUsageVO.from(reply.usage(), runtime.pricing());
        saveAnalysis(dto.jobId(), null, AiAnalysisType.JD_ANALYZE, reply, usage, null, vo);
        return new AiCallVO<>(vo, usage, reply.mocked());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiCallVO<ResumeMatchVO> matchResume(ResumeMatchDTO dto) {
        Job job = dto.jobId() == null ? null : jobService.requireOwned(dto.jobId());
        Resume resume = dto.resumeId() == null ? null : resumeService.requireOwned(dto.resumeId());

        String jd = StringUtils.hasText(dto.jobDescription())
                ? dto.jobDescription()
                : (job == null ? null : job.getJobDescription());
        String resumeContent = StringUtils.hasText(dto.resumeContent())
                ? dto.resumeContent()
                : (resume == null ? null : buildResumeText(resume));

        if (!StringUtils.hasText(jd)) {
            throw new BusinessException(ErrorCode.AI_EMPTY_INPUT, "缺少职位描述，请选择职位或粘贴 JD");
        }
        if (!StringUtils.hasText(resumeContent)) {
            throw new BusinessException(ErrorCode.AI_EMPTY_INPUT, "缺少简历内容，请选择简历或粘贴简历文本");
        }

        AiRuntimeConfig runtime = aiConfigService.runtimeConfig();
        AiReply reply = aiClient.chat(new AiRequest(
                AiTask.RESUME_MATCH,
                AiPrompts.RESUME_MATCH_SYSTEM,
                AiPrompts.resumeMatchUser(jd, resumeContent),
                Map.of("jd", jd, "resume", resumeContent)), runtime);
        ResumeMatchVO vo = aiClient.parse(reply.content(), ResumeMatchVO.class);
        AiUsageVO usage = AiUsageVO.from(reply.usage(), runtime.pricing());
        saveAnalysis(dto.jobId(), dto.resumeId(), AiAnalysisType.RESUME_MATCH, reply, usage, vo.score(), vo);
        return new AiCallVO<>(vo, usage, reply.mocked());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiCallVO<GeneratedQuestionVO> generateQuestions(GenerateQuestionDTO dto) {
        Job job = dto.jobId() == null ? null : jobService.requireOwned(dto.jobId());
        Resume resume = dto.resumeId() == null ? null : resumeService.requireOwned(dto.resumeId());

        String jd = job == null ? "" : StringUtils.trimWhitespace(job.getJobDescription());
        String resumeContent = resume == null ? "" : buildResumeText(resume);
        if (!StringUtils.hasText(jd) && !StringUtils.hasText(resumeContent)) {
            throw new BusinessException(ErrorCode.AI_EMPTY_INPUT, "请至少提供一个职位或一份简历，AI 才能有针对性地出题");
        }

        List<String> categories = dto.categories() == null ? List.of() : dto.categories().stream()
                .filter(StringUtils::hasText)
                .map(QuestionCategory::normalize)
                .distinct()
                .limit(MAX_CATEGORIES)
                .toList();
        int count = dto.countPerCategory() == null
                ? DEFAULT_COUNT_PER_CATEGORY
                : Math.min(Math.max(dto.countPerCategory(), 1), MAX_COUNT_PER_CATEGORY);
        String difficulty = StringUtils.hasText(dto.difficulty()) ? dto.difficulty().toUpperCase() : "MEDIUM";

        Map<String, Object> inputs = new HashMap<>();
        inputs.put("jd", jd);
        inputs.put("resume", resumeContent);
        inputs.put("categories", categories);
        inputs.put("count", count);
        inputs.put("difficulty", difficulty);

        AiRuntimeConfig runtime = aiConfigService.runtimeConfig();
        AiReply reply = aiClient.chat(new AiRequest(
                AiTask.INTERVIEW_QUESTION,
                AiPrompts.QUESTION_SYSTEM,
                AiPrompts.questionUser(job == null ? "未指定岗位" : job.getJobName(), jd, resumeContent,
                        categories, count, difficultyLabel(difficulty), MAX_CATEGORIES),
                inputs), runtime);

        QuestionPayload payload = aiClient.parse(reply.content(), QuestionPayload.class);
        List<InterviewQuestionVO> questions = limitQuestions(payload.questions(), categories, count);
        int saved = Boolean.TRUE.equals(dto.save()) ? saveQuestions(dto.jobId(), questions) : 0;

        AiUsageVO usage = AiUsageVO.from(reply.usage(), runtime.pricing());
        saveAnalysis(dto.jobId(), dto.resumeId(), AiAnalysisType.INTERVIEW_QUESTION, reply, usage, null,
                Map.of("total", questions.size(), "saved", saved));
        return new AiCallVO<>(new GeneratedQuestionVO(questions, saved), usage, reply.mocked());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiCallVO<GeneratedQuestionVO> extractQuestionsFromReview(ExtractQuestionsDTO dto) {
        Job job = dto.jobId() == null ? null : jobService.requireOwned(dto.jobId());
        String review = StringUtils.trimWhitespace(dto.review());
        if (!StringUtils.hasText(review)) {
            throw new BusinessException(ErrorCode.AI_EMPTY_INPUT, "请先填写面试复盘内容");
        }

        Map<String, Object> inputs = new HashMap<>();
        inputs.put("review", review);

        AiRuntimeConfig runtime = aiConfigService.runtimeConfig();
        AiReply reply = aiClient.chat(new AiRequest(
                AiTask.REVIEW_QUESTIONS,
                AiPrompts.REVIEW_QUESTIONS_SYSTEM,
                AiPrompts.reviewQuestionsUser(review, job == null ? null : job.getJobName()),
                inputs), runtime);

        QuestionPayload payload = aiClient.parse(reply.content(), QuestionPayload.class);
        // 复盘提取的题目数量由内容决定，不套用「每分类 N 道」的裁剪规则，只去重防重复入库
        List<InterviewQuestionVO> questions = distinctQuestions(payload.questions());
        int saved = Boolean.FALSE.equals(dto.save()) ? 0 : saveQuestions(dto.jobId(), questions);

        AiUsageVO usage = AiUsageVO.from(reply.usage(), runtime.pricing());
        saveAnalysis(dto.jobId(), null, AiAnalysisType.REVIEW_QUESTIONS, reply, usage, null,
                Map.of("total", questions.size(), "saved", saved));
        return new AiCallVO<>(new GeneratedQuestionVO(questions, saved), usage, reply.mocked());
    }

    /** 复盘里同一道题容易被复述两遍，按题干去重，避免题库里出现重复条目 */
    static List<InterviewQuestionVO> distinctQuestions(List<InterviewQuestionVO> questions) {
        if (questions == null || questions.isEmpty()) {
            return List.of();
        }
        Map<String, InterviewQuestionVO> unique = new java.util.LinkedHashMap<>();
        for (InterviewQuestionVO question : questions) {
            if (!StringUtils.hasText(question.question())) {
                continue;
            }
            unique.putIfAbsent(question.question().trim(), question);
        }
        return List.copyOf(unique.values());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiCallVO<ResumeOptimizeVO> optimizeResume(ResumeOptimizeDTO dto) {
        Job job = dto.jobId() == null ? null : jobService.requireOwned(dto.jobId());
        Resume resume = dto.resumeId() == null ? null : resumeService.requireOwned(dto.resumeId());

        String jd = StringUtils.hasText(dto.jobDescription())
                ? dto.jobDescription()
                : (job == null ? null : job.getJobDescription());
        String rawContent = StringUtils.hasText(dto.rawContent())
                ? dto.rawContent()
                : (resume == null ? null : buildResumeText(resume));

        if (!StringUtils.hasText(jd)) {
            throw new BusinessException(ErrorCode.AI_EMPTY_INPUT, "缺少职位描述，请选择目标岗位或粘贴 JD");
        }
        if (!StringUtils.hasText(rawContent)) {
            throw new BusinessException(ErrorCode.AI_EMPTY_INPUT, "缺少简历内容，请选择简历或粘贴项目经历等素材");
        }

        String jobName = job == null ? null : job.getJobName();
        String focus = StringUtils.trimWhitespace(dto.focus());

        AiRuntimeConfig runtime = aiConfigService.runtimeConfig();
        AiReply reply = aiClient.chat(new AiRequest(
                AiTask.RESUME_OPTIMIZE,
                AiPrompts.RESUME_OPTIMIZE_SYSTEM,
                AiPrompts.resumeOptimizeUser(jobName, jd, rawContent, focus,
                        dto.supplementalProjects(), Boolean.TRUE.equals(dto.allowFabrication())),
                Map.of("jd", jd, "resume", rawContent, "focus", focus == null ? "" : focus)), runtime);

        ResumeOptimizeVO parsed = aiClient.parse(reply.content(), ResumeOptimizeVO.class);
        // 用户没勾选允许补全时，AI 返回的 newProjects 强制丢弃，避免误用
        List<ResumeNewProjectVO> newProjects = Boolean.TRUE.equals(dto.allowFabrication())
                ? parsed.newProjects()
                : List.of();
        ResumeOptimizeVO vo = parsed.withSavedResumeId(saveOptimizedResume(dto, jobName, parsed.optimizedContent(), resumeService))
                .withNewProjects(newProjects);

        AiUsageVO usage = AiUsageVO.from(reply.usage(), runtime.pricing());
        saveAnalysis(dto.jobId(), dto.resumeId(), AiAnalysisType.RESUME_OPTIMIZE, reply, usage, null, vo);
        return new AiCallVO<>(vo, usage, reply.mocked());
    }

    /**
     * 把优化结果另存成一份新简历，而不是覆盖原简历 —— 优化稿是否符合预期由用户判断，
     * 保留原稿才敢让用户直接点「保存」。模型没给出正文时不落库，避免存出一份空简历。
     */
    private Long saveOptimizedResume(ResumeOptimizeDTO dto, String jobName, String optimizedContent,
                                     ResumeService resumeService) {
        if (!Boolean.TRUE.equals(dto.save()) || !StringUtils.hasText(optimizedContent)) {
            return null;
        }
        Resume base = dto.resumeId() == null ? null : resumeService.requireOwned(dto.resumeId());
        ResumeDTO resume = new ResumeDTO(
                optimizeResumeTitle(dto.title(), base == null ? null : base.getTitle(), jobName, resumeService, dto.resumeId()),
                base == null ? null : base.getName(),
                base == null ? null : base.getPhone(),
                base == null ? null : base.getEmail(),
                base == null ? null : base.getEducation(),
                base == null ? null : base.getWorkYears(),
                base == null ? null : base.getSkills(),
                base == null ? null : base.getSummary(),
                optimizedContent,
                false,
                null,
                // 优化后的新版本沿用原简历的头像与版式，用户不用重新上传
                base == null ? null : base.getAvatar(),
                base == null ? null : base.getStyleJson());
        return resumeService.create(resume);
    }

    /**
     * 生成新简历的名称：用户填了就用用户的，否则在原简历名后面挂上岗位和版本号，方便区分版本。
     * <p>title 落库时是 VARCHAR(100)，拼完超长要截断，否则插入直接报错。</p>
     * <p>版本号规则：同一份简历 + 同一个岗位，第 N 次优化就挂 N，从 1 开始递增。</p>
     */
    static String optimizeResumeTitle(String customTitle, String baseTitle, String jobName,
                                      ResumeService resumeService, Long resumeId) {
        if (StringUtils.hasText(customTitle)) {
            return truncate(customTitle.trim(), MAX_RESUME_TITLE_LENGTH);
        }
        String prefix = StringUtils.hasText(baseTitle) ? baseTitle.trim() : "我的简历";
        String suffix = StringUtils.hasText(jobName) ? jobName.trim() + "优化版" : "优化版";
        int version = nextVersion(resumeService, resumeId, prefix, suffix);
        String versionSuffix = version > 1 ? " v" + version : "";
        return truncate(prefix + "-" + suffix + versionSuffix, MAX_RESUME_TITLE_LENGTH);
    }

    /**
     * 计算同一份简历对同一个岗位的第几次优化：从已有简历标题里找最大版本号，加 1 返回。
     */
    private static int nextVersion(ResumeService resumeService, Long resumeId, String prefix, String suffix) {
        if (resumeService == null || resumeId == null) {
            return 1;
        }
        String basePattern = prefix + "-" + suffix;
        int maxVersion = 0;
        for (Resume r : resumeService.listMine()) {
            if (!r.getId().equals(resumeId) && r.getTitle() != null && r.getTitle().startsWith(basePattern)) {
                String tail = r.getTitle().substring(basePattern.length());
                if (tail.isEmpty()) {
                    maxVersion = Math.max(maxVersion, 1);
                } else if (tail.startsWith(" v")) {
                    try {
                        maxVersion = Math.max(maxVersion, Integer.parseInt(tail.substring(2)));
                    } catch (NumberFormatException ignored) {
                        // 版本号不是数字，忽略
                    }
                }
            }
        }
        return maxVersion + 1;
    }

    // ---------------------------------------------------------------- 简历结构化

    /**
     * 把纯文本简历识别成固定结构，并把结果写回 resume.content_json，供前端排版 A4 简历。
     * 只覆盖结构化 JSON，不动简历正文，识别得不对重跑一次即可。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiCallVO<ResumeStructureVO> structureResume(ResumeStructureDTO dto) {
        Resume resume = dto.resumeId() == null ? null : resumeService.requireOwned(dto.resumeId());
        String rawContent = StringUtils.hasText(dto.content())
                ? dto.content()
                : (resume == null ? null : buildResumeText(resume));
        if (!StringUtils.hasText(rawContent)) {
            throw new BusinessException(ErrorCode.AI_EMPTY_INPUT, "缺少简历内容，请选择简历或粘贴简历文本");
        }

        AiRuntimeConfig runtime = aiConfigService.runtimeConfig();
        AiReply reply = aiClient.chat(new AiRequest(
                AiTask.RESUME_STRUCTURE,
                AiPrompts.RESUME_STRUCTURE_SYSTEM,
                AiPrompts.resumeStructureUser(rawContent),
                Map.of("resume", rawContent)), runtime);

        ResumeStructureVO vo = aiClient.parse(reply.content(), ResumeStructureVO.class);
        if (resume != null) {
            saveContentJson(resume, vo);
        }

        AiUsageVO usage = AiUsageVO.from(reply.usage(), runtime.pricing());
        saveAnalysis(null, dto.resumeId(), AiAnalysisType.RESUME_STRUCTURE, reply, usage, null, vo);
        return new AiCallVO<>(vo, usage, reply.mocked());
    }

    /** 结构化结果写回原简历：只覆盖 content_json，标题 / 正文等字段原样保留 */
    private void saveContentJson(Resume base, ResumeStructureVO vo) {
        String contentJson = writeJson(vo);
        if (contentJson == null) {
            return;
        }
        resumeService.update(base.getId(), new ResumeDTO(base.getTitle(), base.getName(), base.getPhone(),
                base.getEmail(), base.getEducation(), base.getWorkYears(), base.getSkills(), base.getSummary(),
                base.getContent(), base.getIsDefault() != null && base.getIsDefault() == 1, contentJson,
                // 这里是把结构写回原简历，头像和版式必须原样带上，否则一排版就被清空
                base.getAvatar(), base.getStyleJson()));
    }
    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    @Override
    public List<AiAnalysis> history(String analysisType, Long jobId, Long resumeId, int limit) {
        return analysisMapper.selectList(new LambdaQueryWrapper<AiAnalysis>()
                .eq(AiAnalysis::getUserId, SecurityUtils.getUserId())
                .eq(StringUtils.hasText(analysisType), AiAnalysis::getAnalysisType, analysisType)
                .eq(jobId != null, AiAnalysis::getJobId, jobId)
                .eq(resumeId != null, AiAnalysis::getResumeId, resumeId)
                .orderByDesc(AiAnalysis::getCreatedAt)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 50)));
    }

    /** 把简历的结构化字段拼成一段文本，方便模型理解 */
    private String buildResumeText(Resume resume) {
        StringBuilder sb = new StringBuilder();
        appendIfPresent(sb, "姓名", resume.getName());
        appendIfPresent(sb, "学历", resume.getEducation());
        appendIfPresent(sb, "工作年限", resume.getWorkYears() == null ? null : resume.getWorkYears() + " 年");
        appendIfPresent(sb, "技能", resume.getSkills());
        appendIfPresent(sb, "个人简介", resume.getSummary());
        appendIfPresent(sb, "简历正文", resume.getContent());
        return sb.toString();
    }

    /** 难度码转成给模型看的中文，模型对「中等」这类中文描述更稳。 */
    static String difficultyLabel(String difficulty) {
        return switch (difficulty) {
            case "EASY" -> "简单";
            case "HARD" -> "困难";
            default -> "中等";
        };
    }

    /**
     * 模型不一定严格遵守「每个分类 N 道」，这里按设置裁剪一遍，保证这个配置真的生效。
     * <p>分类由用户指定时只限制每个分类的数量；交给 AI 决定分类时，额外限制分类个数，
     * 否则返回的题目总数会完全失控（出现过 7 个分类 × 3 道 = 21 道的情况）。</p>
     */
    static List<InterviewQuestionVO> limitQuestions(List<InterviewQuestionVO> questions,
                                                    List<String> categories, int countPerCategory) {
        if (questions == null || questions.isEmpty()) {
            return List.of();
        }
        boolean autoCategories = categories == null || categories.isEmpty();
        Map<String, Integer> taken = new HashMap<>();
        List<InterviewQuestionVO> limited = new ArrayList<>();
        for (InterviewQuestionVO question : questions) {
            String category = StringUtils.hasText(question.category()) ? question.category() : "综合";
            Integer used = taken.get(category);
            if (used == null) {
                if (autoCategories && taken.size() >= MAX_CATEGORIES) {
                    continue;
                }
                used = 0;
            }
            if (used >= countPerCategory) {
                continue;
            }
            taken.put(category, used + 1);
            limited.add(question);
        }
        return limited;
    }

    private void appendIfPresent(StringBuilder sb, String label, String value) {
        if (StringUtils.hasText(value)) {
            sb.append(label).append("：").append(value).append('\n');
        }
    }

    private int saveQuestions(Long jobId, List<InterviewQuestionVO> questions) {
        Long userId = SecurityUtils.getUserId();
        int saved = 0;
        for (InterviewQuestionVO vo : questions) {
            if (!StringUtils.hasText(vo.question())) {
                continue;
            }
            InterviewQuestion entity = new InterviewQuestion();
            entity.setUserId(userId);
            entity.setJobId(jobId);
            // 模型可能自创分类名，统一归一化成固定分类，避免题库里出现同义不同名的分类
            entity.setCategory(QuestionCategory.normalize(vo.category()));
            entity.setQuestion(vo.question());
            entity.setAnswer(vo.answer());
            entity.setDifficulty(StringUtils.hasText(vo.difficulty()) ? vo.difficulty() : "MEDIUM");
            entity.setSource("AI");
            entity.setMastered(0);
            questionMapper.insert(entity);
            saved++;
        }
        return saved;
    }

    private void saveAnalysis(Long jobId, Long resumeId, String type, AiReply reply, AiUsageVO usage,
                              Integer score, Object payload) {
        AiAnalysis analysis = new AiAnalysis();
        analysis.setUserId(SecurityUtils.getUserId());
        analysis.setJobId(jobId);
        analysis.setResumeId(resumeId);
        analysis.setAnalysisType(type);
        analysis.setModel(reply.model());
        analysis.setScore(score);
        if (usage != null) {
            analysis.setInputTokens(usage.inputTokens());
            analysis.setOutputTokens(usage.outputTokens());
            analysis.setCachedTokens(usage.cachedTokens());
            analysis.setReasoningTokens(usage.reasoningTokens());
            analysis.setEstimatedCost(usage.estimatedCost());
        }
        analysis.setResult(writeJson(payload));
        analysisMapper.insert(analysis);
    }

    private String writeJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("AI 结果序列化失败", e);
            return null;
        }
    }

    /** 模型返回的题目包装结构 */
    private record QuestionPayload(List<InterviewQuestionVO> questions) {
    }
}
