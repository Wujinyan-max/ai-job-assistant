package com.jobassistant.service;

import com.jobassistant.ai.AiClient;
import com.jobassistant.config.AiProperties;
import com.jobassistant.ai.AiReply;
import com.jobassistant.ai.AiRuntimeConfig;
import com.jobassistant.ai.AiUsage;
import com.jobassistant.ai.MockAiEngine;
import com.jobassistant.ai.AiProtocolCodec;
import com.jobassistant.common.BusinessException;
import com.jobassistant.common.ErrorCode;
import com.jobassistant.service.impl.ResumeVisionServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 视觉识别必须「永远不挡住导入」：模型看不了图、调用失败、没配 Key，都要能降级并给出正确提示。
 */
class ResumeVisionServiceImplTest {

    private static final String RESUME_TEXT = """
            张三
            应聘岗位：Java 后端开发工程师
            教育背景
            某某大学  计算机科学与技术  本科

            专业技能
            熟悉 Java、Spring Boot、MySQL
            """;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("模型看不了图：降级文本识别，并提示换成多模态模型")
    void fallsBackWhenModelRejectsImages() {
        ResumeVisionService.VisionOutcome outcome = recognizeWith(runtime("deepseek-v4-pro"),
                failing("HTTP 400：This model does not support image_url content"));

        assertThat(outcome.source()).isEqualTo(ResumeVisionService.Source.TEXT);
        assertThat(outcome.notice()).contains("deepseek-v4-pro").contains("多模态");
        // 降级后结构不能是空的，否则用户导入完什么都拿不到
        assertThat(outcome.vision().structure().basics().name()).isEqualTo("张三");
    }

    @Test
    @DisplayName("普通调用失败（超时 / 额度）也说清原因，不能都推给「模型不支持图片」")
    void reportsRealReasonOnGenericFailure() {
        ResumeVisionService.VisionOutcome outcome = recognizeWith(runtime("deepseek-flash"),
                failing("已连上 https://api.deepseek.com 但读取响应超时"));

        assertThat(outcome.source()).isEqualTo(ResumeVisionService.Source.TEXT);
        assertThat(outcome.notice()).contains("读取响应超时");
        assertThat(outcome.notice()).doesNotContain("不支持图片识别");
    }

    @Test
    @DisplayName("模型正常返回时按视觉识别处理，版式和内容都取模型的")
    void usesVisionResultWhenModelAnswers() {
        ResumeVisionService.VisionOutcome outcome = recognizeWith(runtime("deepseek-flash"),
                content("""
                        {"structure":{"basics":{"name":"李四","label":"测试工程师"},
                         "work":[{"company":"某某科技","position":"测试工程师","period":"2023.07-至今",
                         "bullets":["负责接口自动化用例编写"]}]},
                         "style":{"accentColor":"#1a73e8","headerBand":true,"skillsColumns":2}}
                        """));

        assertThat(outcome.source()).isEqualTo(ResumeVisionService.Source.VISION);
        assertThat(outcome.notice()).isNull();
        assertThat(outcome.vision().structure().basics().name()).isEqualTo("李四");
        assertThat(outcome.vision().structure().work()).hasSize(1);
        assertThat(outcome.vision().style().headerBand()).isTrue();
        assertThat(outcome.vision().style().skillsColumns()).isEqualTo(2);
    }

    @Test
    @DisplayName("没配 API Key 时走本地规则解析，且不提示「换模型」")
    void usesLocalParserWithoutApiKey() {
        ResumeVisionService.VisionOutcome outcome = recognizeWith(runtimeWithoutKey(), (request, config) -> {
            throw new AssertionError("没有 Key 时不应该发起任何模型调用");
        });

        assertThat(outcome.source()).isEqualTo(ResumeVisionService.Source.LOCAL);
        assertThat(outcome.notice()).isNull();
        assertThat(outcome.vision().structure().basics().name()).isEqualTo("张三");
    }

    @Test
    @DisplayName("渲染不出图片（不是 PDF）时降级文本识别，不能抛异常给导入接口")
    void fallsBackWhenPagesCannotBeRendered() {
        AiRuntimeConfig config = runtime("deepseek-flash");
        ResumeVisionServiceImpl service = new ResumeVisionServiceImpl(client(config,
                (request, runtime) -> {
                    throw new AssertionError("渲染不出图片时不应该调用模型");
                }), configService(config));

        ResumeVisionService.VisionOutcome outcome = service.recognize("not-a-pdf".getBytes(), RESUME_TEXT);

        assertThat(outcome.source()).isEqualTo(ResumeVisionService.Source.TEXT);
        assertThat(outcome.notice()).contains("渲染不出");
    }

    // ------------------------------------------------------------------ 测试脚手架

    /** 让 AiClient 用固定配置启动，避免依赖真实用户配置 */
    private static AiRuntimeConfig runtime(String model) {
        return new AiRuntimeConfig("DEEPSEEK", AiProtocolCodec.RESPONSES,
                "https://api.deepseek.com/v1", "test-key", model, 90);
    }

    private static AiRuntimeConfig runtimeWithoutKey() {
        return new AiRuntimeConfig("DEEPSEEK", AiProtocolCodec.RESPONSES,
                "https://api.deepseek.com/v1", null, "deepseek-flash", 90);
    }

    private ResumeVisionService.VisionOutcome recognizeWith(AiRuntimeConfig config, Responder responder) {
        return new ResumeVisionServiceImpl(client(config, responder), configService(config))
                .recognize(pdf(), RESUME_TEXT);
    }

    /** AiConfigService 有三个方法，不是函数接口，测试里用最小桩实现顶掉 */
    private static AiConfigService configService(AiRuntimeConfig config) {
        return new AiConfigService() {
            @Override
            public com.jobassistant.vo.AiConfigVO getMaskedConfig() {
                return null;
            }

            @Override
            public com.jobassistant.vo.AiConfigVO save(com.jobassistant.dto.AiConfigSaveDTO dto) {
                return null;
            }

            @Override
            public AiRuntimeConfig runtimeConfig() {
                return config;
            }
        };
    }

    /** 固定返回一段模型文本 */
    private Responder content(String json) {
        return (request, config) -> new AiReply(json, config.model(), false, AiUsage.EMPTY);
    }

    /** 固定抛出调用失败 */
    private Responder failing(String message) {
        return (request, config) -> {
            throw new BusinessException(ErrorCode.AI_CALL_FAILED, message);
        };
    }

    private interface Responder {
        AiReply reply(com.jobassistant.ai.AiRequest request, AiRuntimeConfig config);
    }

    /** 用假的 RestClient 行为包出 AiClient：只替换发起网络请求这一步 */
    private AiClient client(AiRuntimeConfig config, Responder responder) {
        return new AiClient(properties(config), new MockAiEngine(mapper), mapper, new AiProtocolCodec()) {
            @Override
            public AiReply chat(com.jobassistant.ai.AiRequest request, AiRuntimeConfig runtime) {
                return responder.reply(request, runtime);
            }
        };
    }

    private static AiProperties properties(AiRuntimeConfig config) {
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        return properties;
    }

    private static byte[] pdf() {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
