package com.gengzi.response;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JwtResponse {

    private String token;
    private String type = "Bearer";
    private String id;
    private String username;
    private List<String> role;

    public JwtResponse(String accessToken, String id, String username, List<String> role) {
        this.token = accessToken;
        this.id = id;
        this.username = username;
        this.role = role;
    }
}
