package com.jobassistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jobassistant.ai.QuestionCategory;
import com.jobassistant.common.BusinessException;
import com.jobassistant.common.ErrorCode;
import com.jobassistant.common.PageResult;
import com.jobassistant.entity.InterviewQuestion;
import com.jobassistant.mapper.InterviewQuestionMapper;
import com.jobassistant.security.SecurityUtils;
import com.jobassistant.service.QuestionService;
import com.jobassistant.vo.QuestionCategoryStatsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class QuestionServiceImpl implements QuestionService {

    private final InterviewQuestionMapper questionMapper;

    @Override
    public PageResult<InterviewQuestion> page(int pageNum, int pageSize, String category, Integer mastered, Long jobId) {
        LambdaQueryWrapper<InterviewQuestion> wrapper = new LambdaQueryWrapper<InterviewQuestion>()
                .eq(InterviewQuestion::getUserId, SecurityUtils.getUserId())
                .eq(StringUtils.hasText(category), InterviewQuestion::getCategory, category)
                .eq(mastered != null, InterviewQuestion::getMastered, mastered)
                .eq(jobId != null, InterviewQuestion::getJobId, jobId)
                .orderByDesc(InterviewQuestion::getCreatedAt);
        return PageResult.of(questionMapper.selectPage(new Page<>(pageNum, pageSize), wrapper));
    }

    @Override
    public List<String> categories() {
        // 按固定分类顺序排序，让筛选下拉框的顺序稳定下来
        return questionMapper.selectCategories(SecurityUtils.getUserId()).stream()
                .sorted(QuestionCategory.canonicalOrder())
                .toList();
    }

    @Override
    public List<QuestionCategoryStatsVO> categoryStats() {
        // 同样按固定分类顺序排序，分类卡片的顺序才不会每次打开都在变
        return questionMapper.selectCategoryStats(SecurityUtils.getUserId()).stream()
                .sorted(Comparator.comparing(QuestionCategoryStatsVO::getCategory, QuestionCategory.canonicalOrder()))
                .toList();
    }

    @Override
    public List<String> categoryOptions() {
        return QuestionCategory.labels();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markMastered(Long id, boolean mastered) {
        requireOwned(id);
        questionMapper.update(null, new LambdaUpdateWrapper<InterviewQuestion>()
                .eq(InterviewQuestion::getId, id)
                .set(InterviewQuestion::getMastered, mastered ? 1 : 0));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireOwned(id);
        questionMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // 带上 userId 条件，防止越权删除别人的题目
        questionMapper.delete(new LambdaQueryWrapper<InterviewQuestion>()
                .in(InterviewQuestion::getId, ids)
                .eq(InterviewQuestion::getUserId, SecurityUtils.getUserId()));
    }

    private void requireOwned(Long id) {
        Long count = questionMapper.selectCount(new LambdaQueryWrapper<InterviewQuestion>()
                .eq(InterviewQuestion::getId, id)
                .eq(InterviewQuestion::getUserId, SecurityUtils.getUserId()));
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.QUESTION_NOT_FOUND);
        }
    }
}
