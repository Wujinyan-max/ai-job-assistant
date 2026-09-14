package com.jobassistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jobassistant.common.BusinessException;
import com.jobassistant.common.ErrorCode;
import com.jobassistant.common.PageResult;
import com.jobassistant.dto.ResumeDTO;
import com.jobassistant.entity.Resume;
import com.jobassistant.mapper.ResumeMapper;
import com.jobassistant.security.SecurityUtils;
import com.jobassistant.service.ResumeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ResumeServiceImpl implements ResumeService {

    private final ResumeMapper resumeMapper;

    @Override
    public PageResult<Resume> page(int pageNum, int pageSize, String keyword) {
        LambdaQueryWrapper<Resume> wrapper = new LambdaQueryWrapper<Resume>()
                .eq(Resume::getUserId, SecurityUtils.getUserId())
                .and(StringUtils.hasText(keyword), w -> w
                        .like(Resume::getTitle, keyword)
                        .or().like(Resume::getSkills, keyword))
                .orderByDesc(Resume::getIsDefault)
                .orderByDesc(Resume::getUpdatedAt);
        return PageResult.of(resumeMapper.selectPage(new Page<>(pageNum, pageSize), wrapper));
    }

    @Override
    public List<Resume> listMine() {
        return resumeMapper.selectList(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getUserId, SecurityUtils.getUserId())
                .orderByDesc(Resume::getIsDefault)
                .orderByDesc(Resume::getUpdatedAt));
    }

    @Override
    public Resume requireOwned(Long id) {
        Resume resume = resumeMapper.selectOne(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getId, id)
                .eq(Resume::getUserId, SecurityUtils.getUserId()));
        if (resume == null) {
            throw new BusinessException(ErrorCode.RESUME_NOT_FOUND);
        }
        return resume;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ResumeDTO dto) {
        Long userId = SecurityUtils.getUserId();
        Resume resume = new Resume();
        copy(dto, resume);
        resume.setUserId(userId);

        // 第一份简历自动设为默认
        Long count = resumeMapper.selectCount(new LambdaQueryWrapper<Resume>().eq(Resume::getUserId, userId));
        boolean makeDefault = Boolean.TRUE.equals(dto.isDefault()) || count == null || count == 0;
        resume.setIsDefault(makeDefault ? 1 : 0);
        resumeMapper.insert(resume);

        if (makeDefault) {
            clearOtherDefault(userId, resume.getId());
        }
        return resume.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ResumeDTO dto) {
        Resume resume = requireOwned(id);
        copy(dto, resume);
        if (Boolean.TRUE.equals(dto.isDefault())) {
            resume.setIsDefault(1);
        }
        resumeMapper.updateById(resume);
        if (Boolean.TRUE.equals(dto.isDefault())) {
            clearOtherDefault(resume.getUserId(), id);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Resume resume = requireOwned(id);
        resumeMapper.deleteById(id);
        // 删掉的是默认简历，就把剩下最新的一份设为默认
        if (resume.getIsDefault() != null && resume.getIsDefault() == 1) {
            List<Resume> rest = resumeMapper.selectList(new LambdaQueryWrapper<Resume>()
                    .eq(Resume::getUserId, resume.getUserId())
                    .orderByDesc(Resume::getUpdatedAt)
                    .last("LIMIT 1"));
            if (!rest.isEmpty()) {
                setDefault(rest.get(0).getId());
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long id) {
        Resume resume = requireOwned(id);
        resumeMapper.update(null, new LambdaUpdateWrapper<Resume>()
                .eq(Resume::getUserId, resume.getUserId())
                .set(Resume::getIsDefault, 0));
        resumeMapper.update(null, new LambdaUpdateWrapper<Resume>()
                .eq(Resume::getId, id)
                .set(Resume::getIsDefault, 1));
    }

    private void clearOtherDefault(Long userId, Long keepId) {
        resumeMapper.update(null, new LambdaUpdateWrapper<Resume>()
                .eq(Resume::getUserId, userId)
                .ne(Resume::getId, keepId)
                .set(Resume::getIsDefault, 0));
    }

    private void copy(ResumeDTO dto, Resume resume) {
        resume.setTitle(dto.title());
        resume.setName(dto.name());
        resume.setPhone(dto.phone());
        resume.setEmail(dto.email());
        resume.setEducation(dto.education());
        resume.setWorkYears(dto.workYears());
        resume.setSkills(dto.skills());
        resume.setSummary(dto.summary());
        resume.setContent(dto.content());
    }
}
