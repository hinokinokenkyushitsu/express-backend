package com.fiveok.express.user.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @Size(max = 32, message = "{validation.name.size}")
    private String name;

    @Size(max = 512, message = "{validation.avatar.size}")
    private String avatar;
}
