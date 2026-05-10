package com.fiveok.express.order.enums;

import lombok.Getter;

/**
 * 订单紧急度。
 * <p>
 * 同时承担"承诺时间上限"的业务规则：
 * 紧急单 ≤ 30 分钟，普通单 ≤ 120 分钟。
 * 这条上限的意义是：防止快递员承诺一个无意义的大值（比如 9999 分钟）来规避超时。
 * </p>
 */
@Getter
public enum OrderUrgency {

    NORMAL(0, "普通", 120),
    URGENT(1, "紧急", 30);

    private final int code;
    private final String desc;
    /** 该紧急度允许的最大承诺分钟数 */
    private final int maxPromisedMinutes;

    OrderUrgency(int code, String desc, int maxPromisedMinutes) {
        this.code = code;
        this.desc = desc;
        this.maxPromisedMinutes = maxPromisedMinutes;
    }

    public static OrderUrgency of(int code) {
        for (OrderUrgency u : values()) {
            if (u.code == code) return u;
        }
        throw new IllegalArgumentException("未知紧急度: " + code);
    }
}
