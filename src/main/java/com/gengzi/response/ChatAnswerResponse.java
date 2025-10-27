package com.gengzi.response;

import com.gengzi.dao.entity.RagReference;
import lombok.Data;

@Data
public class ChatAnswerResponse {


    private String answer;


    private RagReference reference;

}
