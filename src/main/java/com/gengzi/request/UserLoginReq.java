package com.gengzi.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.Value;

import java.io.Serializable;

/**
 * DTO for {@link com.gengzi.dao.User}
 */
@Data
public class UserLoginReq implements Serializable {
    @NotNull
    @Size(max = 255)
    String password;
    @NotNull
    @Size(max = 255)
    String username;
}