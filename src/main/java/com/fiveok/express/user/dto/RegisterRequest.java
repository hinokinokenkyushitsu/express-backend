package com.fiveok.express.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "{validation.name.notBlank}")
    @Size(max = 32, message = "{validation.name.size}")
    private String name;

    @NotBlank(message = "{validation.phone.notBlank}")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "{validation.phone.format}")
    private String phone;

    @NotBlank(message = "{validation.password.notBlank}")
    @Size(min = 6, max = 64, message = "{validation.password.size}")
    private String password;

    /** 注册角色：USER / COURIER。为空时默认 USER；不允许前端注册 ADMIN。 */
    private String role;
}
