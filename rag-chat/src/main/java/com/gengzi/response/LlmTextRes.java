package com.gengzi.response;


import com.gengzi.entity.RagReference;
import lombok.Data;

@Data
public class LlmTextRes {

    private String answer;

    private RagReference reference;

}
