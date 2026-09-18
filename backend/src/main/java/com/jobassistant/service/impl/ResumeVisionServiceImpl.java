package com.jobassistant.service.impl;

import com.jobassistant.ai.AiClient;
import com.jobassistant.ai.AiPrompts;
import com.jobassistant.ai.AiReply;
import com.jobassistant.ai.AiRequest;
import com.jobassistant.ai.AiRuntimeConfig;
import com.jobassistant.ai.AiTask;
import com.jobassistant.ai.ResumeStructureParser;
import com.jobassistant.common.BusinessException;
import com.jobassistant.service.AiConfigService;
import com.jobassistant.service.ResumeVisionService;
import com.jobassistant.vo.ResumeStructureVO;
import com.jobassistant.vo.ResumeStyleVO;
import com.jobassistant.vo.ResumeVisionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 简历视觉识别实现：PDF 渲染成页面图片交给多模态模型，一次读出内容结构与版式。
 *
 * <p>识别失败绝不能影响导入，所以任何异常都在这里消化掉，退回纯文本识别或本地规则解析，
 * 并把原因放进 {@code notice} 交给前端提示用户。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeVisionServiceImpl implements ResumeVisionService {

    /** 上游报错里出现这些字样，说明问题出在「这个模型看不了图」，而不是网络或额度 */
    private static final List<String> VISION_REJECT_MARKERS = List.of(
            "not supported", "unsupported", "不支持", "invalid content", "invalid type",
            "image_url", "input_image", "vision", "multimodal", "unknown variant",
            "does not support", "cannot process image");

    private final AiClient aiClient;

    private final AiConfigService aiConfigService;

    @Override
    public VisionOutcome recognize(byte[] pdfBytes, String fallbackText) {
        String text = fallbackText == null ? "" : fallbackText;
        // 先把文本兜底算出来：后面无论哪一步失败都能直接用它，不用重复解析
        ResumeStructureVO localStructure = ResumeStructureParser.parse(text);

        AiRuntimeConfig runtime;
        try {
            runtime = aiConfigService.runtimeConfig();
        } catch (Exception e) {
            log.warn("读取 AI 配置失败，简历导入退回本地规则解析", e);
            return new VisionOutcome(new ResumeVisionVO(localStructure, ResumeStyleVO.empty()),
                    Source.LOCAL, null);
        }
        if (runtime == null || !StringUtils.hasText(runtime.apiKey())) {
            // 没配 Key 的用户本来就用不了模型，不需要提示「换模型」，本地规则解析是他们预期内的结果
            return new VisionOutcome(new ResumeVisionVO(localStructure, ResumeStyleVO.empty()),
                    Source.LOCAL, null);
        }

        List<String> images = ResumeVisionRenderer.render(pdfBytes);
        if (images.isEmpty()) {
            return textOutcome(localStructure, "这份 PDF 渲染不出页面图片，已改用文本识别。");
        }

        try {
            AiReply reply = aiClient.chat(new AiRequest(
                    AiTask.RESUME_VISION,
                    AiPrompts.RESUME_VISION_SYSTEM,
                    AiPrompts.resumeVisionUser(images.size()),
                    Map.of("resume", text),
                    images), runtime);
            if (reply.mocked()) {
                // 未配置 Key 时 AiClient 会静默走本地模拟引擎，结果里没有版式，按本地解析处理
                return new VisionOutcome(new ResumeVisionVO(localStructure, ResumeStyleVO.empty()),
                        Source.LOCAL, null);
            }
            ResumeVisionVO vision = aiClient.parse(reply.content(), ResumeVisionVO.class);
            return new VisionOutcome(vision, Source.VISION, null);
        } catch (Exception e) {
            String reason = e instanceof BusinessException ? e.getMessage() : e.getClass().getSimpleName();
            log.warn("简历视觉识别失败，降级为文本识别，model={}, 原因：{}", runtime.model(), reason);
            return textOutcome(localStructure, buildFallbackNotice(runtime.model(), reason));
        }
    }

    /** 降级到纯文本识别：结构用本地规则解析的结果，版式交给 {@link ResumeStyleExtractor} 的本地提取 */
    private VisionOutcome textOutcome(ResumeStructureVO localStructure, String notice) {
        return new VisionOutcome(new ResumeVisionVO(localStructure, ResumeStyleVO.empty()),
                Source.TEXT, notice);
    }

    /**
     * 区分「模型看不了图」和「这次调用运气不好」：前者要建议用户换多模态模型，
     * 后者只需要说明失败原因，否则会把用量超限、网络超时这类问题误导成模型能力不足。
     */
    private static String buildFallbackNotice(String model, String reason) {
        String detail = reason == null ? "" : reason;
        String lower = detail.toLowerCase(Locale.ROOT);
        boolean modelCannotSeeImages = VISION_REJECT_MARKERS.stream()
                .anyMatch(marker -> lower.contains(marker));
        String head = "当前模型 " + (StringUtils.hasText(model) ? model : "（未命名）");
        if (modelCannotSeeImages) {
            return head + " 不支持图片识别，已改用文本识别。"
                    + "换成多模态模型（例如 deepseek-flash）可以获得更准确的版式与内容还原。";
        }
        return head + " 的视觉识别调用失败（" + detail + "），已改用文本识别。"
                + "如果希望按原版式还原，请换成支持图片的多模态模型（例如 deepseek-flash）后重试。";
    }
}
