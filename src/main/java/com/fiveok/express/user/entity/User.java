package com.fiveok.express.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("users")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String phone;

    /** 默认查询不返回密码字段 */
    @TableField(select = false)
    private String password;

    private String avatar;

    private String role;

    /** 快递员累计超时次数（USER 角色固定为 0） */
    private Integer overdueCount;

    /** 封禁到期时间（NULL = 未封禁） */
    private LocalDateTime banUntil;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
