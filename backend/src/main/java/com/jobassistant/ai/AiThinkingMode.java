package com.jobassistant.ai;

/**
 * 思考模式（思维链）开关。厂商默认多为开启，DeepSeek 默认开启且强度为 high，
 * 关闭后能明显减少输出 token。
 */
public final class AiThinkingMode {

    /** 跟随厂商默认：请求里不额外传参 */
    public static final String DEFAULT = "DEFAULT";
    /** 关闭思考 */
    public static final String OFF = "OFF";
    /** 开启思考，强度由厂商默认决定 */
    public static final String ON = "ON";

    public static boolean isValid(String mode) {
        return mode == null || DEFAULT.equals(mode) || OFF.equals(mode) || ON.equals(mode);
    }

    /** 空值统一按「跟随厂商默认」处理 */
    public static String normalize(String mode) {
        return OFF.equals(mode) || ON.equals(mode) ? mode : DEFAULT;
    }

    private AiThinkingMode() {
    }
}
