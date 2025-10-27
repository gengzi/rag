package com.gengzi.response;

import lombok.Data;

import java.util.List;

/**
 * 分块详情
 */
@Data
public class ChunkDetails {

    /**
     * 分块id
     */
    private String id;
    // 内容与元数据字段（原有的 content 保留，新增扩展字段）
    private String content;
    // 页码信息（如"[5]"，对应 page_num_int）
    private String pageNumInt;

    private List<String> img;
}
