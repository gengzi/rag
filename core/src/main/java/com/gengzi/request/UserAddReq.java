package com.gengzi.request;

import lombok.Data;

import java.util.List;

@Data
public class UserAddReq {


    private String username;

    private String nickname;

    private String password;

    private Boolean isSuperuser;

    private List<String> knowledgeIds;


}
