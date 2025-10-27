package com.gengzi.ui.service;


import com.gengzi.request.DocumentSearchReq;
import com.gengzi.response.DocumentDetailsResponse;
import com.gengzi.response.DocumentPreviewResponse;
import com.gengzi.response.ImagePreviewResponse;

import java.util.List;
import java.util.Map;

/**
 * 文档相关操作service
 */
public interface DocumentService {


    void documentToEmbedding(String documentId);


    DocumentPreviewResponse documentPreview(String documentId);

    Map<String, Object> search(DocumentSearchReq documentSearchReq);

    List<?> documentChunks(String kbId);

    DocumentDetailsResponse documentChunksDetails(String documentId);

    ImagePreviewResponse documentImgPreview(String imgkey);

}
