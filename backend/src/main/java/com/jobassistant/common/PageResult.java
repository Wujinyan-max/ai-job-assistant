package com.jobassistant.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.function.Function;

/**
 * 统一分页返回结构。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分页结果")
public class PageResult<T> {

    @Schema(description = "总记录数")
    private long total;

    @Schema(description = "当前页码，从 1 开始")
    private long pageNum;

    @Schema(description = "每页条数")
    private long pageSize;

    @Schema(description = "当前页数据")
    private List<T> records;

    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
    }

    /** 把实体分页转换为 VO 分页。 */
    public static <E, T> PageResult<T> of(IPage<E> page, Function<E, T> converter) {
        List<T> records = page.getRecords().stream().map(converter).toList();
        return new PageResult<>(page.getTotal(), page.getCurrent(), page.getSize(), records);
    }
}
