package com.gengzi.response;


import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper=false)
public class LlmTextRes extends ContentRes{

    private String answer;

    private RagReference reference;

}
