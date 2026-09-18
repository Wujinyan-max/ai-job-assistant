package com.jobassistant.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 投递状态机是整个看板和统计口径的基础，这里把它的契约固定下来。
 */
class ApplicationStatusTest {

    @Test
    @DisplayName("isValid 只接受定义好的状态值")
    void isValidOnlyAcceptsDefinedValues() {
        assertThat(ApplicationStatus.isValid(ApplicationStatus.APPLIED)).isTrue();
        assertThat(ApplicationStatus.isValid(ApplicationStatus.OFFER)).isTrue();

        assertThat(ApplicationStatus.isValid("applied")).isFalse();
        assertThat(ApplicationStatus.isValid("UNKNOWN")).isFalse();
        assertThat(ApplicationStatus.isValid("")).isFalse();
        assertThat(ApplicationStatus.isValid(null)).isFalse();
    }

    @Test
    @DisplayName("7 个状态都能翻译成中文，未知值兜底为「未知」")
    void everyStatusHasChineseLabel() {
        for (String status : ApplicationStatus.ALL) {
            assertThat(ApplicationStatus.label(status))
                    .as("状态 %s 缺少中文标签", status)
                    .isNotBlank()
                    .isNotEqualTo("未知");
        }

        assertThat(ApplicationStatus.label(ApplicationStatus.APPLIED)).isEqualTo("已投递");
        assertThat(ApplicationStatus.label(ApplicationStatus.WRITTEN_TEST)).isEqualTo("笔试");
        assertThat(ApplicationStatus.label(null)).isEqualTo("未知");
    }

    @Test
    @DisplayName("看板首列固定是「已收藏」，且收藏不算真正投递")
    void wishlistIsNotCountedAsSubmitted() {
        assertThat(ApplicationStatus.ALL).hasSize(7);
        assertThat(ApplicationStatus.ALL.get(0)).isEqualTo(ApplicationStatus.WISHLIST);
        assertThat(ApplicationStatus.ALL).containsExactly(
                "WISHLIST", "APPLIED", "WRITTEN_TEST", "INTERVIEW", "OFFER", "REJECTED", "CLOSED");

        assertThat(ApplicationStatus.SUBMITTED).doesNotContain(ApplicationStatus.WISHLIST);
        assertThat(ApplicationStatus.SUBMITTED).containsExactly(
                "APPLIED", "WRITTEN_TEST", "INTERVIEW", "OFFER", "REJECTED", "CLOSED");
    }

    @Test
    @DisplayName("CLOSED 的文案是「已放弃」，枚举值保持不变")
    void closedReadsAsGivingUp() {
        assertThat(ApplicationStatus.label(ApplicationStatus.CLOSED)).isEqualTo("已放弃");
        assertThat(ApplicationStatus.CLOSED).isEqualTo("CLOSED");
    }
}
