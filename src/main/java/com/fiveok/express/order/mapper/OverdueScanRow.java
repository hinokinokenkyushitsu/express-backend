package com.fiveok.express.order.mapper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 定时扫描超时订单时使用的轻量行对象。
 * <p>
 * 只装两列（id + courierId），避免一次拉整个 Order 实体。
 * 这是 mapper 的私有数据载体，不跨模块暴露。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OverdueScanRow {
    private Long id;
    private Long courierId;
}
