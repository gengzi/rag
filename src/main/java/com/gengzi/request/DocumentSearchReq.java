package com.gengzi.request;

import lombok.Data;

@Data
public class DocumentSearchReq {

    /**
     * 问题
     */
    private String query;

    /**
     * 页码
     */
    private int page;

    /**
     * 每页数量
     */
    private int pageSize;


}
