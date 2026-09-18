package com.jobassistant.service;

import com.jobassistant.vo.ResumeVisionVO;

/**
 * 简历视觉识别：把 PDF 渲染成页面图片交给多模态模型，一次读出内容结构与版式。
 */
public interface ResumeVisionService {

    /**
     * 识别结果的来源，用于给前端提示用户当前走了哪条路。
     */
    enum Source {

        /** 多模态模型看图识别 */
        VISION("视觉识别"),

        /** 文本模型按纯文本识别（模型不支持图片或视觉调用失败时降级） */
        TEXT("文本识别"),

        /** 未配置 API Key，走本地规则解析 */
        LOCAL("本地规则解析"),

        /** 视觉不可用且没有降级数据，什么都没识别出来 */
        NONE("未识别");

        private final String label;

        Source(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /**
     * 视觉识别结果，附带实际走了哪条路径以及给用户的提示。
     *
     * @param vision 识别到的内容与版式
     * @param source 实际使用的识别方式
     * @param notice 需要提示用户的话，没有时为 null（例如模型不支持图片）
     */
    record VisionOutcome(ResumeVisionVO vision, Source source, String notice) {
    }

    /**
     * 用 PDF 原文件做视觉识别，失败时自动降级。
     *
     * @param pdfBytes     PDF 原文件字节，非 PDF 或为空时直接走降级
     * @param fallbackText 降级用的纯文本简历正文
     */
    VisionOutcome recognize(byte[] pdfBytes, String fallbackText);
}