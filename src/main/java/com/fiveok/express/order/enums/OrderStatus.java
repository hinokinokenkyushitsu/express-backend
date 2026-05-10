package com.fiveok.express.order.enums;

import lombok.Getter;

@Getter
public enum OrderStatus {

    PENDING(0, "待抢单"),
    IN_PROGRESS(1, "配送中"),
    COMPLETED(2, "已完成"),
    CANCELLED(3, "已取消");

    private final int code;
    private final String desc;

    OrderStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static OrderStatus of(int code) {
        for (OrderStatus s : values()) {
            if (s.code == code) return s;
        }
        throw new IllegalArgumentException("未知订单状态: " + code);
    }
}
