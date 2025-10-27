package com.gengzi.response;


import lombok.Data;

import java.util.List;

@Data
public class DocumentDetailsResponse {

    /**
     * 文档id
     */
    private String id;

    /**
     * 文档名称
     */
    private String name;

    /**
     * 创建时间
     */
    private Long createTime;


    /**
     * 文件大小
     */
    private Long size;

    /**
     * 文件类型
     */
    private String contentType;


    /**
     * 文档拆分数量
     */
    private Integer chunkNum;



    private List<ChunkDetails> chunkDetails;

}
