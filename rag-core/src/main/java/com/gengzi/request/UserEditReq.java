package com.gengzi.request;

import lombok.Data;

import java.util.List;

@Data
public class UserEditReq {

    private String id;

    private String nickname;

    private String password;

    private Boolean isSuperuser;

    private List<String> knowledgeIds;


}
