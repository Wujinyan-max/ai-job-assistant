package com.jobassistant.common;

import java.util.List;

/**
 * 投递状态常量（求职流程的核心状态机）。
 * <pre>
 * WISHLIST -> APPLIED -> WRITTEN_TEST -> INTERVIEW -> OFFER
 *                    \-> REJECTED / CLOSED
 * </pre>
 */
public final class ApplicationStatus {

    /** 已收藏，还没投 */
    public static final String WISHLIST = "WISHLIST";
    /** 已投递 */
    public static final String APPLIED = "APPLIED";
    /** 笔试 */
    public static final String WRITTEN_TEST = "WRITTEN_TEST";
    /** 面试中 */
    public static final String INTERVIEW = "INTERVIEW";
    /** 拿到 Offer */
    public static final String OFFER = "OFFER";
    /** 被拒绝 */
    public static final String REJECTED = "REJECTED";
    /** 主动放弃（不打算继续跟进这条投递） */
    public static final String CLOSED = "CLOSED";

    /** 全部合法状态，顺序即为看板列顺序 */
    public static final List<String> ALL =
            List.of(WISHLIST, APPLIED, WRITTEN_TEST, INTERVIEW, OFFER, REJECTED, CLOSED);

    /** 已经真正投出去的（用于计算投递量） */
    public static final List<String> SUBMITTED =
            List.of(APPLIED, WRITTEN_TEST, INTERVIEW, OFFER, REJECTED, CLOSED);

    private ApplicationStatus() {
    }

    public static boolean isValid(String status) {
        return status != null && ALL.contains(status);
    }

    public static String label(String status) {
        return switch (status == null ? "" : status) {
            case WISHLIST -> "已收藏";
            case APPLIED -> "已投递";
            case WRITTEN_TEST -> "笔试";
            case INTERVIEW -> "面试";
            case OFFER -> "Offer";
            case REJECTED -> "已拒绝";
            case CLOSED -> "已放弃";
            default -> "未知";
        };
    }
}
