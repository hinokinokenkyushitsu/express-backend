package com.fiveok.express.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("orders")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long courierId;

    private String stationAddress;

    private String targetAddress;

    private String pickupCode;

    private BigDecimal amount;

    private Integer status;

    /** 紧急度：0 普通，1 紧急。下单时由用户选择，决定快递员可承诺的最长时间。 */
    private Integer urgency;

    /** 快递员承诺的送达分钟数（NULL = 未抢单） */
    private Integer promisedMinutes;

    /** 抢单时刻（NULL = 未抢单） */
    private LocalDateTime grabTime;

    /** 截止时刻 = grabTime + promisedMinutes，前端用它做倒计时 */
    private LocalDateTime deadline;

    /**
     * 是否已被认定超时。
     * 用 CAS（overdue=0 → 1）保证一个订单只会触发一次结算（金额清零 + 累加快递员超时数）。
     */
    private Integer overdue;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
