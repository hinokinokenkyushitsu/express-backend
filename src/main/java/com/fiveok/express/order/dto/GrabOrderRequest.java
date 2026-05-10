package com.fiveok.express.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 快递员抢单时提交的承诺信息。
 * <p>
 * 业务规则：
 * <ul>
 *   <li>必须承诺一个分钟数（1~120）</li>
 *   <li>是否合法（紧急≤30，普通≤120）由 service 层根据订单 urgency 二次校验</li>
 *   <li>这里 DTO 层只做"格式正确"的兜底校验</li>
 * </ul>
 * </p>
 */
@Data
public class GrabOrderRequest {

    @NotNull(message = "{validation.promisedMinutes.notNull}")
    @Min(value = 1,   message = "{validation.promisedMinutes.range}")
    @Max(value = 120, message = "{validation.promisedMinutes.range}")
    private Integer promisedMinutes;
}
