package com.fiveok.express.base.utils;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 统一响应封装
 * <p>
 * 携带 {@code serverTime}：前端做"配送倒计时"时，应该用 (deadline - serverTime)
 * 而不是 (deadline - 客户端本地时间)，避免用户本地时钟不准导致计时错误。
 * </p>
 */
@Data
public class Result<T> {

    private int code;
    private String msg;
    private T data;
    /** 服务器当前时间（每次响应都带，前端用它校准倒计时） */
    private LocalDateTime serverTime;

    private Result(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
        this.serverTime = LocalDateTime.now();
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(200, "成功", data);
    }

    public static <T> Result<T> success() {
        return new Result<>(200, "成功", null);
    }

    public static <T> Result<T> error(int code, String msg) {
        return new Result<>(code, msg, null);
    }

    public static <T> Result<T> error(String msg) {
        return new Result<>(500, msg, null);
    }

    public static <T> Result<T> unauthorized(String msg) {
        return new Result<>(401, msg, null);
    }
}
