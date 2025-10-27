package com.gengzi.dao.entity;

import lombok.Data;

@Data
public class ReferenceDocument {

    /**
     * 分块id
     */
    private String chunkId;

    /**
     * 文档id
     */
    private String documentId;

    /**
     * 文档名称
     */
    private String documentName;

    /**
     * 文档预览链接
     */
    private String documentUrl;

    /**
     * 文本段落
     */
    private String text;

    /**
     * 评分
     */
    private String score;

    /**
     * 涉及到的页码
     */
    private String pageRange;


    /**
     * 文档类型
     */
    private String contentType;

    /**
     * 图片链接
     */
    private String imageUrl;

}