package com.gengzi.ui.controller;


import com.gengzi.embedding.load.pdf.OcrPdfReader;
import com.gengzi.embedding.load.pdf.PdfReaderTool;
import com.gengzi.request.AddDocumentByS3;
import com.gengzi.request.DocumentSearchReq;
import com.gengzi.request.KnowledgebaseCreateReq;
import com.gengzi.response.*;
import com.gengzi.ui.service.DocumentService;
import com.gengzi.ui.service.KnowledgeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@Tag(name = "doc管理", description = "doc管理")
public class DocumentController {


    @Autowired
    private OcrPdfReader pyPdfReader;

    @Autowired
    private PdfReaderTool pdfReaderTool;


    @Autowired
    private KnowledgeService knowledgeService;

    @Autowired
    private DocumentService documentService;


    /**
     * 根据登录用户获取所有的知识库
     *
     * @return
     */
    @GetMapping("/api/knowledge-base")
    public Result<List<KnowledgebaseResponse>> knowledgeBase() {
        List<KnowledgebaseResponse> knowledgebase = knowledgeService.getKnowledgebase();
        return Result.success(knowledgebase);
    }

    /**
     * @return
     */
    @PostMapping("/api/knowledge-base/create")
    public Result<Boolean> knowledgeBaseCreate(@RequestBody KnowledgebaseCreateReq req) {
        knowledgeService.createKnowledgebase(req);
        return Result.success(true);
    }

    /**
     * 获取当前知识库下面的文档列表
     *
     * @return
     */
    @GetMapping("/api/knowledge-base/documents")
    @ResponseBody
    public Result<?> knowledgeBaseCreate(@RequestParam(required = true) String kbId,
                                         @PageableDefault(page = 0, size = 10) Pageable pageable) {
        Page<?> documents = knowledgeService.documents(kbId, pageable);
        return Result.success(documents);
    }


    /**
     * 批量文件上传接口
     *
     * @param files 前端传递的批量文件（参数名需与前端一致，如"files"）
     * @return 上传成功的文件URL列表
     */
    @PostMapping("/api/knowledge-base/batch-upload")
    public Result<?> batchUpload(@RequestParam("knowledgeId") String knowledgeId, @RequestParam("files") MultipartFile[] files) {
        // 1. 基础校验：文件数组为空
        if (files == null || files.length == 0) {
            return Result.fail(ResultCode.FILE_UPLOAD_EMPTY.getCode(), ResultCode.FILE_UPLOAD_EMPTY.getMessage());
        }
        knowledgeService.uploadFile(knowledgeId, files);
        // 4. 返回成功结果（包含所有上传成功的文件URL）
        return Result.success(true);
    }


    /**
     * 从s3存储中添加文档到知识库的s3桶中
     *
     * @param addDocumentByS3 代添加文件的s3配置信息
     * @return
     */
    @PostMapping("/document/add")
    @ResponseBody
    public Result<?> documentAdd(@RequestBody AddDocumentByS3 addDocumentByS3) {
        knowledgeService.documentAdd(addDocumentByS3);
        return Result.success(true);
    }


    @PostMapping("/document/upload")
    public String document() {
        return null;
    }

    @PostMapping("/document/embedding/pdf")
    public Result<Void> documentToEmbeddingByPdf(@RequestParam String filePath) {
//        pdfReaderTool.pdfReader(filePath);
//        pyPdfReader.pdfParse(filePath);
        return Result.successMessage("等待解析完成");
    }


    /**
     * 根据文档id进行embedding
     *
     * @param documentId 文档id
     * @return
     */
    @PostMapping("/document/embedding")
    public Result<Void> documentToEmbedding(@RequestParam String documentId) {
        documentService.documentToEmbedding(documentId);
        return Result.successMessage("等待解析完成");
    }


    /**
     * 根据文档id获取文档内容
     *
     * @param documentId 文档id
     * @return
     */
    @GetMapping("/document/{documentId}")
    public ResponseEntity<?> documentPreview(@PathVariable String documentId) {
        try {
            DocumentPreviewResponse documentPreviewResponse = documentService.documentPreview(documentId);
            return ResponseEntity.ok(documentPreviewResponse);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "失败"));
        }
    }


    @PostMapping("/document/search")
    public Map<String, Object> search(@RequestBody DocumentSearchReq req) {
        return documentService.search(req);
    }


    /**
     * 获取现在已经解析过的文档ids
     */
    @GetMapping("/document/chunks")
    @ResponseBody
    public Result<?> documentChunks(@RequestParam String kbId) {
        return Result.success(documentService.documentChunks(kbId));
    }


    /**
     * 获取文档解析后的分块详情信息
     * 知识库，点击查看分块后调用
     */
    @GetMapping("/document/chunks/details")
    @ResponseBody
    public Result<?> documentChunksDetails(@RequestParam String documentId) {
        return Result.success(documentService.documentChunksDetails(documentId));
    }


    @GetMapping("/document/img")
    public ResponseEntity<?> documentImgPreview(@RequestParam String imgkey) {
        try {
            ImagePreviewResponse imagePreviewResponse = documentService.documentImgPreview(imgkey);
            return ResponseEntity.ok(imagePreviewResponse);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "失败"));
        }
    }

    /**
     * 根据登录用户获取所有的知识库
     *
     * @return
     */
    @GetMapping("/api/knowledge/all")
    public Result<List<KnowledgeBasePulldownResponse>> knowledgeBaseAll() {
        List<KnowledgeBasePulldownResponse> knowledgeBaseAll = knowledgeService.knowledgeBaseAll();
        return Result.success(knowledgeBaseAll);
    }


    @GetMapping("/image/{image}/{fileId}")
    public ResponseEntity<?> documentImgPreview(@PathVariable String image, @PathVariable String fileId) {
        try {
            String imgkey = fileId + "/" + image + ".png";
            ImagePreviewResponse imagePreviewResponse = documentService.documentImgPreview(imgkey);
            return ResponseEntity.ok(imagePreviewResponse);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "失败"));
        }
    }


}
