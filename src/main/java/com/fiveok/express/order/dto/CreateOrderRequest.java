package com.fiveok.express.order.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateOrderRequest {

    @NotBlank(message = "{validation.station.notBlank}")
    @Size(max = 256, message = "{validation.station.size}")
    private String stationAddress;

    @NotBlank(message = "{validation.target.notBlank}")
    @Size(max = 256, message = "{validation.target.size}")
    private String targetAddress;

    @NotBlank(message = "{validation.pickupCode.notBlank}")
    @Size(max = 32, message = "{validation.pickupCode.size}")
    private String pickupCode;

    @NotNull(message = "{validation.amount.notNull}")
    @DecimalMin(value = "0.00", message = "{validation.amount.min}")
    private BigDecimal amount;

    /**
     * 紧急度：0 普通，1 紧急。
     * 不传则默认为 0（普通）。前端下单时让用户在两个 chip 里二选一。
     */
    @Min(value = 0, message = "{validation.urgency.range}")
    @Max(value = 1, message = "{validation.urgency.range}")
    private Integer urgency = 0;
}
