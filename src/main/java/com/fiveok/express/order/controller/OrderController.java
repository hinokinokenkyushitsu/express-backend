package com.fiveok.express.order.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fiveok.express.base.utils.Result;
import com.fiveok.express.order.dto.CreateOrderRequest;
import com.fiveok.express.order.dto.GrabOrderRequest;
import com.fiveok.express.order.dto.OrderView;
import com.fiveok.express.order.entity.Order;
import com.fiveok.express.order.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /** 创建订单（同学C 负责） */
    @PostMapping
    public Result<Order> createOrder(@Valid @RequestBody CreateOrderRequest request,
                                     HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        return Result.success(orderService.createOrder(userId, request));
    }

    /** 我的订单（同学D 负责） */
    @GetMapping("/my")
    public Result<IPage<OrderView>> myOrders(
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        return Result.success(orderService.getMyOrders(userId, pageNum, pageSize));
    }

    /** 抢单大厅（同学D 负责） */
    @GetMapping("/hall")
    public Result<IPage<OrderView>> hallOrders(
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize) {
        return Result.success(orderService.getHallOrders(pageNum, pageSize));
    }

    /** 快递员我的任务：查询当前快递员已经抢到的订单 */
    @GetMapping("/courier/my")
    public Result<IPage<OrderView>> courierTasks(
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
            HttpServletRequest httpRequest) {
        Long courierId = (Long) httpRequest.getAttribute("userId");
        return Result.success(orderService.getCourierTasks(courierId, pageNum, pageSize));
    }

    /**
     * 抢单（同学D 负责）。
     * <p>
     * 改造后：抢单时必须在 body 中提交承诺送达时间（promisedMinutes）。
     * 紧急单 ≤ 30 分钟，普通单 ≤ 120 分钟，由 service 校验。
     * 被封禁的快递员（最近 5 单超时累计满阈值）不能抢单。
     * </p>
     */
    @PostMapping("/{orderId}/grab")
    public Result<Void> grabOrder(@PathVariable Long orderId,
                                  @Valid @RequestBody GrabOrderRequest request,
                                  HttpServletRequest httpRequest) {
        Long courierId = (Long) httpRequest.getAttribute("userId");
        orderService.grabOrder(orderId, courierId, request.getPromisedMinutes());
        return Result.success();
    }

    /** 更新订单状态（同学D 负责） */
    @PatchMapping("/{orderId}/status")
    public Result<Void> updateStatus(@PathVariable Long orderId,
                                     @RequestParam @Min(0) @Max(3) Integer status,
                                     HttpServletRequest httpRequest) {
        Long operatorId = (Long) httpRequest.getAttribute("userId");
        String role = (String) httpRequest.getAttribute("role");
        orderService.updateStatus(orderId, status, operatorId, role);
        return Result.success();
    }

    /** 取消订单（同学D 负责） */
    @DeleteMapping("/{orderId}")
    public Result<Void> cancelOrder(@PathVariable Long orderId,
                                    HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        orderService.cancelOrder(orderId, userId);
        return Result.success();
    }
}
