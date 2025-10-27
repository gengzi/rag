package com.gengzi.ui.service;

import com.gengzi.request.AddDocumentByS3;
import com.gengzi.request.KnowledgebaseCreateReq;
import com.gengzi.response.KnowledgeBasePulldownResponse;
import com.gengzi.response.KnowledgebaseResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface KnowledgeService {


    List<KnowledgebaseResponse> getKnowledgebase();


    void createKnowledgebase(KnowledgebaseCreateReq knowledgebaseCreateReq);

    Page<?> documents(String kbId, Pageable pageable);


    void documentAdd(AddDocumentByS3 addDocumentByS3);

    void uploadFile(String knowledgeId, MultipartFile[] files);

    List<KnowledgeBasePulldownResponse> knowledgeBaseAll();

}
