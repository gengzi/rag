package com.gengzi.model;

import com.gengzi.context.FileContext;
import com.gengzi.dao.repository.DocumentRepository;
import com.gengzi.embedding.split.TextSplitterTool;
import com.gengzi.enums.FileProcessStatusEnum;
import com.gengzi.request.LayoutParsingRequest;
import com.gengzi.response.LayoutParsingResponse;
import com.gengzi.vector.es.EsVectorDocumentConverter;
import com.gengzi.vector.es.metadata.KeywordMetadataEnricherByChatModel;
import com.gengzi.vector.es.metadata.SummaryEnricherMetadataByChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 调用PPStructureV3 服务
 */
@Component
public class PPStructureV3Tool {
    private static final Logger logger = LoggerFactory.getLogger(PPStructureV3Tool.class);
    private final RestTemplate restTemplate;
    private final ThreadPoolTaskExecutor threadPoolTaskExecutor;
    @Value("${model.ppStructureV3.url}")
    private String url;
    @Autowired
    private LayoutResponseToDocumentConverter layoutResponseToDocumentConverter;

    @Autowired
    private TextSplitterTool textSplitterTool;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private KeywordMetadataEnricherByChatModel keywordMetadataEnricherByChatModel;

    @Autowired
    private SummaryEnricherMetadataByChatModel summaryEnricherMetadataByChatModel;


    @Autowired
    private DocumentRepository documentRepository;


    @Autowired
    public PPStructureV3Tool(RestTemplate restTemplate, @Qualifier("asyncTaskExecutor") ThreadPoolTaskExecutor threadPoolTaskExecutor) {
        this.restTemplate = restTemplate;
        this.threadPoolTaskExecutor = threadPoolTaskExecutor;
    }


    /**
     * 异步解析PDF版面（非阻塞）
     *
     * @param fileContext 文件属性
     * @return 异步任务标识（可用于前端轮询查询结果）
     */

    @Transactional(rollbackFor = Exception.class)
    public CompletableFuture<LayoutParsingResponse> asyncParsePdf(FileContext fileContext) {
        // 1. 构建入参对象
        LayoutParsingRequest request = new LayoutParsingRequest();
        request.setFile(fileContext.getFileUrl().toString());
        request.setFileType(0); // PDF类型
//        request.setVisualize(false); // 关闭可视化，减少响应体积

        // 2. 调用异步方法（立即返回CompletableFuture，不阻塞主线程）
        CompletableFuture<LayoutParsingResponse> asyncFuture = asyncCallLayoutApi(request);

        // 3. 异步处理结果（回调函数，在异步线程执行）
        asyncFuture
                // 成功回调：处理解析结果（如保存到数据库、发送通知）
                .thenAccept(response -> {
                    if (response != null && response.isSuccess()) {
                        logger.info("异步解析成功！logId：{}", response.getLogId());
                        logger.info("处理页数：{}", response.getResult().getLayoutParsingResults().size());
                        logger.debug("解析结果：{}", response.getResult());
                        // 此处可添加业务逻辑：如保存Markdown结果、Base64图片转存等
                        List<Document> convert = layoutResponseToDocumentConverter.convert(response, fileContext);
                        logger.info("转换后的第一个Document：{}", convert.get(0).getText());
                        // 进行文本分割
                        List<Document> splitDocuments = textSplitterTool.splitCustomized(convert, 500, 200, 100, 10000, false);
                        // 使用ai模型增加关键字
//                        List<Document> documents = keywordMetadataEnricherByChatModel.enrichDocuments(splitDocuments);

//                        List<Document> documents1 = summaryEnricherMetadataByChatModel.enrichDocuments(documents);
                        // 丰富存入向量库内容
                        List<Document> convert1 = EsVectorDocumentConverter.convert(splitDocuments, fileContext);

                        vectorStore.add(convert1);

                        // 将文档标记为已处理

                        documentRepository.updateChunkNumAndStatusById(fileContext.getDocumentId(), splitDocuments.size(), String.valueOf(FileProcessStatusEnum.PROCESS_SUCCESS.getCode()));

                    } else {
                        logger.error("异步解析失败！错误信息：{}", response != null ? response.getErrorMsg() : "未知错误");
                    }
                })
                // 异常回调：处理网络异常、解析异常等
                .exceptionally(ex -> {
                    ex.printStackTrace();
                    logger.error("异步调用发生异常：" + ex.getMessage());
                    // 可选：记录异常日志、重试逻辑等
                    return null;
                });
        return asyncFuture;
    }

    /**
     * 异步调用版面解析接口
     *
     * @param request 入参对象
     * @return CompletableFuture<LayoutParsingResponse> 异步结果对象
     * 可通过 thenAccept()/thenApply() 处理成功结果，exceptionally() 处理异常
     */
    //@Async("asyncTaskExecutor") // 核心注解：标记该方法为异步执行，由Spring线程池调度
    public CompletableFuture<LayoutParsingResponse> asyncCallLayoutApi(LayoutParsingRequest request) {
        try {
            // 1. 构建请求头（JSON格式）
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

            // 2. 封装请求体
            HttpEntity<LayoutParsingRequest> httpEntity = new HttpEntity<>(request, headers);

            // 3. 同步调用改为异步返回（CompletableFuture.supplyAsync() 包装同步逻辑）
            return CompletableFuture.supplyAsync(() -> {
                // 底层仍用RestTemplate同步调用，但执行在异步线程池
                return restTemplate.postForObject(url, httpEntity, LayoutParsingResponse.class);
            }, threadPoolTaskExecutor);
        } catch (RestClientException e) {
            // 捕获请求前的异常（如参数序列化失败），返回异常的CompletableFuture
            CompletableFuture<LayoutParsingResponse> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }


}
