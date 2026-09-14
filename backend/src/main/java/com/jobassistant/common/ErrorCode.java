package com.jobassistant.common;

import lombok.Getter;

/**
 * 统一业务错误码。
 * <p>1xxx 用户相关，2xxx 业务数据相关，6xxx AI 相关。</p>
 */
@Getter
public enum ErrorCode {

    SUCCESS(200, "操作成功"),
    PARAM_ERROR(400, "请求参数不合法"),
    UNAUTHORIZED(401, "未登录或登录状态已过期"),
    FORBIDDEN(403, "没有访问权限"),
    NOT_FOUND(404, "请求的资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),
    CONFLICT(409, "数据状态冲突"),
    INTERNAL_ERROR(500, "系统开小差了，请稍后再试"),

    USERNAME_EXISTS(1001, "用户名已被注册"),
    USER_NOT_FOUND(1002, "用户不存在"),
    PASSWORD_ERROR(1003, "用户名或密码错误"),
    OLD_PASSWORD_ERROR(1004, "原密码不正确"),
    USER_DISABLED(1005, "账号已被禁用，请联系管理员"),
    NOT_LOGIN(1006, "请先登录"),

    RESUME_NOT_FOUND(2001, "简历不存在"),
    COMPANY_NOT_FOUND(2002, "公司不存在"),
    JOB_NOT_FOUND(2003, "职位不存在"),
    APPLICATION_NOT_FOUND(2004, "投递记录不存在"),
    INTERVIEW_NOT_FOUND(2005, "面试记录不存在"),
    APPLICATION_EXISTS(2006, "该职位已经加入投递记录"),
    STATUS_INVALID(2007, "状态值不合法"),
    DATA_NOT_BELONG_TO_USER(2008, "无权操作他人的数据"),
    QUESTION_NOT_FOUND(2009, "面试题不存在"),

    AI_DISABLED(6001, "AI 能力未开启"),
    AI_CALL_FAILED(6002, "AI 服务调用失败，请稍后重试"),
    AI_PARSE_FAILED(6003, "AI 返回内容无法解析"),
    AI_EMPTY_INPUT(6004, "请先填写需要分析的内容");

    private final Integer code;
    private final String message;

    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
