package com.jobassistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobassistant.ai.AiClient;
import com.jobassistant.ai.AiPrompts;
import com.jobassistant.ai.AiReply;
import com.jobassistant.ai.AiRequest;
import com.jobassistant.ai.AiTask;
import com.jobassistant.common.AiAnalysisType;
import com.jobassistant.common.BusinessException;
import com.jobassistant.common.ErrorCode;
import com.jobassistant.dto.AiConfigSaveDTO;
import com.jobassistant.dto.GenerateQuestionDTO;
import com.jobassistant.dto.JdAnalyzeDTO;
import com.jobassistant.dto.ResumeMatchDTO;
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
import com.jobassistant.vo.GeneratedQuestionVO;
import com.jobassistant.vo.InterviewQuestionVO;
import com.jobassistant.vo.JdAnalysisVO;
import com.jobassistant.vo.ResumeMatchVO;
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
    @Transactional(rollbackFor = Exception.class)
    public JdAnalysisVO analyzeJd(JdAnalyzeDTO dto) {
        Job job = dto.jobId() == null ? null : jobService.requireOwned(dto.jobId());
        String jd = StringUtils.hasText(dto.jobDescription())
                ? dto.jobDescription()
                : (job == null ? null : job.getJobDescription());
        if (!StringUtils.hasText(jd)) {
            throw new BusinessException(ErrorCode.AI_EMPTY_INPUT, "请先粘贴 JD 或选择一个已保存的职位");
        }

        AiReply reply = aiClient.chat(new AiRequest(
                AiTask.JD_ANALYZE,
                AiPrompts.JD_ANALYZE_SYSTEM,
                AiPrompts.jdAnalyzeUser(jd),
                Map.of("jd", jd)), aiConfigService.runtimeConfig());
        JdAnalysisVO vo = aiClient.parse(reply.content(), JdAnalysisVO.class);
        saveAnalysis(dto.jobId(), null, AiAnalysisType.JD_ANALYZE, reply, null, vo);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResumeMatchVO matchResume(ResumeMatchDTO dto) {
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

        AiReply reply = aiClient.chat(new AiRequest(
                AiTask.RESUME_MATCH,
                AiPrompts.RESUME_MATCH_SYSTEM,
                AiPrompts.resumeMatchUser(jd, resumeContent),
                Map.of("jd", jd, "resume", resumeContent)), aiConfigService.runtimeConfig());
        ResumeMatchVO vo = aiClient.parse(reply.content(), ResumeMatchVO.class);
        saveAnalysis(dto.jobId(), dto.resumeId(), AiAnalysisType.RESUME_MATCH, reply, vo.score(), vo);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GeneratedQuestionVO generateQuestions(GenerateQuestionDTO dto) {
        Job job = dto.jobId() == null ? null : jobService.requireOwned(dto.jobId());
        Resume resume = dto.resumeId() == null ? null : resumeService.requireOwned(dto.resumeId());

        String jd = job == null ? "" : StringUtils.trimWhitespace(job.getJobDescription());
        String resumeContent = resume == null ? "" : buildResumeText(resume);
        if (!StringUtils.hasText(jd) && !StringUtils.hasText(resumeContent)) {
            throw new BusinessException(ErrorCode.AI_EMPTY_INPUT, "请至少提供一个职位或一份简历，AI 才能有针对性地出题");
        }

        List<String> categories = dto.categories() == null ? List.of() : dto.categories().stream()
                .filter(StringUtils::hasText)
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

        AiReply reply = aiClient.chat(new AiRequest(
                AiTask.INTERVIEW_QUESTION,
                AiPrompts.QUESTION_SYSTEM,
                AiPrompts.questionUser(job == null ? "未指定岗位" : job.getJobName(), jd, resumeContent,
                        categories, count, difficulty),
                inputs), aiConfigService.runtimeConfig());

        QuestionPayload payload = aiClient.parse(reply.content(), QuestionPayload.class);
        List<InterviewQuestionVO> questions = payload.questions() == null ? List.of() : payload.questions();
        int saved = Boolean.TRUE.equals(dto.save()) ? saveQuestions(dto.jobId(), questions) : 0;

        saveAnalysis(dto.jobId(), dto.resumeId(), AiAnalysisType.INTERVIEW_QUESTION, reply, null,
                Map.of("total", questions.size(), "saved", saved));
        return new GeneratedQuestionVO(questions, saved);
    }

    @Override
    public List<AiAnalysis> history(String analysisType, int limit) {
        return analysisMapper.selectList(new LambdaQueryWrapper<AiAnalysis>()
                .eq(AiAnalysis::getUserId, SecurityUtils.getUserId())
                .eq(StringUtils.hasText(analysisType), AiAnalysis::getAnalysisType, analysisType)
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
            entity.setCategory(StringUtils.hasText(vo.category()) ? vo.category() : "综合");
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

    private void saveAnalysis(Long jobId, Long resumeId, String type, AiReply reply,
                              Integer score, Object payload) {
        AiAnalysis analysis = new AiAnalysis();
        analysis.setUserId(SecurityUtils.getUserId());
        analysis.setJobId(jobId);
        analysis.setResumeId(resumeId);
        analysis.setAnalysisType(type);
        analysis.setModel(reply.model());
        analysis.setScore(score);
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
