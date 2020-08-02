package com.easy.restful.common.core.exception;


import com.easy.restful.common.core.common.status.ResultCode;

/**
 * 全局异常
 *
 * @author tengchong
 * @date 2019-08-30
 */
public enum GlobalException implements EasyServiceException {

    // 请求参数有误
    BAD_REQUEST(ResultCode.BAD_REQUEST.getCode(),"请求参数有误"),
    // 会话信息已过期
    USERNAME_NOT_FOUND(ResultCode.UNAUTHORIZED.getCode(), "用户名或密码错误"),
    // 会话信息已过期
    SESSION_INVALID(ResultCode.UNAUTHORIZED.getCode(), "会话信息已过期"),
    // 用户在其他地方登录
    SESSION_LOGIN_ELSEWHERE(ResultCode.UNAUTHORIZED.getCode(), "用户在其他地方登录，你被迫退出"),
    // 被管理员强制踢出
    SESSION_FORCE_LOGOUT(ResultCode.UNAUTHORIZED.getCode(), "被管理员强制退出"),
    // 你无权限访问此资源
    FORBIDDEN(ResultCode.FORBIDDEN.getCode(), "你无权限访问此资源"),
    // 你访问的资源不存在
    HTTP_NOT_FOUND(ResultCode.NOT_FOUND.getCode(), "你访问的资源不存在"),
    // 要删除的信息包含子节点
    EXIST_CHILD("200000", "要删除的信息包含子节点，请移除子节点后重试"),
    // 无效的日期格式
    INVALID_DATE_FORMAT("200400", "无效的日期格式");
    /**
     * 错误代码
     */
    private String code;
    /**
     * 错误信息
     */
    private String message;

    GlobalException(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
