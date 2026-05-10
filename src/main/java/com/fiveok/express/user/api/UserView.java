package com.fiveok.express.user.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户视图对象（跨模块对外暴露）
 * <p>
 * 这是 user 模块对外唯一可见的"用户信息"形式。
 * 其他模块（如 order）需要查询用户信息时，拿到的是这个 UserView，
 * 而不是内部的 User 实体——避免泄露密码、deleted 等敏感字段。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserView {

    private Long id;
    private String name;
    private String phone;
    private String avatar;
    private String role;

    /**
     * 快递员累计超时次数（USER 角色固定为 0）。
     * 让前端能在快递员个人页展示"超时进度 3/5"这样的提示。
     */
    private Integer overdueCount;

    /**
     * 封禁到期时间（NULL = 未封禁）。
     * 实际"是否封禁中"由前端根据当前时间和该字段判断，或调用 isCourierBanned。
     */
    private LocalDateTime banUntil;
}
