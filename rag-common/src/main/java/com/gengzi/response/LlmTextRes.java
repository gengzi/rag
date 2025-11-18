package com.gengzi.response;



import lombok.Data;

@Data
public class LlmTextRes {

    private String answer;

    private RagReference reference;

}
