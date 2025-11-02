package com.gengzi.request;


import lombok.Data;

import java.util.List;

@Data
public class EvaluateCreateReq {


    private String batchNum;

    private String kbId;

    private List<String> singleDocumentIds;

    private List<List<String>> multipleDocumentIds;

    private boolean colloquial;


}
