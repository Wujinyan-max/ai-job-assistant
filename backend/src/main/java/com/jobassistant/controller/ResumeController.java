package com.jobassistant.controller;

import com.jobassistant.common.PageResult;
import com.jobassistant.common.Result;
import com.jobassistant.dto.ResumeDTO;
import com.jobassistant.entity.Resume;
import com.jobassistant.service.ResumeImportService;
import com.jobassistant.service.ResumeService;
import com.jobassistant.vo.ResumeImportVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "04-简历管理", description = "支持维护多个简历版本")
@RestController
@RequestMapping("/resumes")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;

    private final ResumeImportService resumeImportService;

    @Operation(summary = "分页查询简历")
    @GetMapping
    public Result<PageResult<Resume>> page(@RequestParam(defaultValue = "1") int pageNum,
                                           @RequestParam(defaultValue = "10") int pageSize,
                                           @RequestParam(required = false) String keyword) {
        return Result.success(resumeService.page(pageNum, pageSize, keyword));
    }

    @Operation(summary = "查询全部简历", description = "下拉选择用，不分页")
    @GetMapping("/all")
    public Result<List<Resume>> listAll() {
        return Result.success(resumeService.listMine());
    }

    @Operation(summary = "查询简历详情")
    @GetMapping("/{id}")
    public Result<Resume> detail(@PathVariable Long id) {
        return Result.success(resumeService.requireOwned(id));
    }

    @Operation(summary = "新增简历")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody ResumeDTO dto) {
        return Result.success(resumeService.create(dto));
    }

    @Operation(summary = "修改简历")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ResumeDTO dto) {
        resumeService.update(id, dto);
        return Result.success();
    }

    @Operation(summary = "删除简历")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        resumeService.delete(id);
        return Result.success();
    }

    @Operation(summary = "设为默认简历")
    @PutMapping("/{id}/default")
    public Result<Void> setDefault(@PathVariable Long id) {
        resumeService.setDefault(id);
        return Result.success();
    }

    @Operation(summary = "导入简历文件", description = "上传 PDF / DOCX / TXT 简历，解析出姓名、联系方式、学历、技能等信息供表单回填")
    @PostMapping("/import")
    public Result<ResumeImportVO> importFile(@RequestParam("file") MultipartFile file) {
        return Result.success(resumeImportService.parse(file));
    }
}
