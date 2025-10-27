package com.gengzi.request;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AddDocumentByS3 {

    /**
     * 存储桶名称
     */
    private String bucket;

    /**
     * “目录”
     */
    private String key;

    private String kbId;

}
