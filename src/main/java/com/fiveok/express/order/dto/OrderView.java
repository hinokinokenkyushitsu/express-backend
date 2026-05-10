package com.fiveok.express.order.dto;

import com.fiveok.express.order.entity.Order;
import com.fiveok.express.user.api.UserView;
import lombok.Data;

/**
 * 订单视图（带用户信息）
 * <p>
 * 演示跨模块协作：order 列表展示时通常需要附带"发单人姓名/头像"等用户信息。
 * 这里 order 模块通过 UserFacade 获取 UserView 拼装到 OrderView 里。
 * </p>
 */
@Data
public class OrderView {

    private Order order;

    /** 发单人信息（来自 user 模块的 UserFacade，可能为 null） */
    private UserView user;

    /** 接单快递员信息（可能为 null：未抢单） */
    private UserView courier;
}
