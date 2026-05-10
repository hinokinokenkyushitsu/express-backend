package com.fiveok.express.base.exception;

import lombok.Getter;

/**
 * 业务异常
 * <p>
 * <b>i18n 改造</b>：异常存储的是消息 key（如 "order.grab.taken"），
 * 而不是已翻译的字符串。真正的翻译延迟到全局异常处理器中根据请求 Locale 完成。
 * </p>
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;
    private final String messageKey;
    private final Object[] args;

    public BusinessException(String messageKey) {
        this(500, messageKey);
    }

    public BusinessException(int code, String messageKey, Object... args) {
        super(messageKey);          // super.message 仍存 key，方便日志排查
        this.code = code;
        this.messageKey = messageKey;
        this.args = args;
    }

    public static BusinessException notFound(String resourceNameKey) {
        return new BusinessException(404, "common.not.found", new Object[]{ resourceNameKey });
    }

    public static BusinessException forbidden(String messageKey, Object... args) {
        return new BusinessException(403, messageKey, args);
    }

    public static BusinessException conflict(String messageKey, Object... args) {
        return new BusinessException(409, messageKey, args);
    }
}
