package com.jobassistant.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 接口路径写错、或者后端还跑着不含该接口的旧版本时，必须明确回「接口不存在」。
 * 这类 404 一旦被 Exception 兜底吞成 500「系统开小差了」，排查方向会被带偏一大截
 * （曾经把「后端没重启」看成「服务端故障」）。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("访问不存在的接口回 404 和具体路径，而不是 500")
    void unknownPathIsReportedAsNotFound() {
        MockHttpServletRequest request =
                new MockHttpServletRequest(HttpMethod.POST.name(), "/api/ai/optimize-resume");

        Result<Void> result = handler.handleNoResourceFound(
                new NoResourceFoundException(HttpMethod.POST, "ai/optimize-resume"), request);

        assertThat(result.getCode()).isEqualTo(ErrorCode.NOT_FOUND.getCode());
        assertThat(result.getMessage()).contains("/api/ai/optimize-resume");
    }

    @Test
    @DisplayName("真正的服务端异常仍然回 500")
    void unexpectedErrorStillReturnsInternalError() {
        MockHttpServletRequest request =
                new MockHttpServletRequest(HttpMethod.POST.name(), "/api/ai/optimize-resume");

        Result<Void> result = handler.handleException(new NullPointerException("boom"), request);

        assertThat(result.getCode()).isEqualTo(ErrorCode.INTERNAL_ERROR.getCode());
    }
}
