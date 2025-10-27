package com.gengzi.embedding.load.word;


import com.gengzi.config.S3Properties;
import com.gengzi.context.DocumentMetadataMap;
import com.gengzi.context.FileContext;
import com.gengzi.dao.Document;
import com.gengzi.dao.repository.DocumentRepository;
import com.gengzi.embedding.split.RAGMarkdownSplitter;
import com.gengzi.embedding.split.TextSplitterTool;
import com.gengzi.enums.BlockType;
import com.gengzi.enums.FileProcessStatusEnum;
import com.gengzi.enums.S3FileType;
import com.gengzi.request.MarkItDownRequest;
import com.gengzi.s3.S3ClientUtils;
import com.gengzi.utils.*;
import com.gengzi.vector.es.EsVectorDocumentConverter;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 通过markItDown转换word
 *
 * @author: gengzi
 */
@Component
public class WordConvertByMarkItDownReader {

    public static final String WORD_MIMETYPE = "application/msword";
    private static final Logger logger = LoggerFactory.getLogger(WordConvertByMarkItDownReader.class);
    private final EncodingRegistry registry = Encodings.newLazyEncodingRegistry();
    private final Encoding encoding = this.registry.getEncoding(EncodingType.CL100K_BASE);
    @Autowired
    private S3Properties s3Properties;
    @Autowired
    private S3ClientUtils s3ClientUtils;
    @Autowired
    private TextSplitterTool textSplitterTool;
    @Autowired
    private VectorStore vectorStore;
    @Autowired
    private WebClient webClient;
    @Value("${model.markItDown.url}")
    private String url;
    @Autowired
    @Qualifier("asyncTaskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;
    @Autowired
    private DocumentRepository documentRepository;
    @Autowired
    @Qualifier("openAiChatModel")
    private ChatModel chatModel;

    @Autowired
    private ChatClient chatClientImage;

    public void wordParse(String filePath, Document document) {
        // 调用markItDown api服务，将word转为markdown
        String documentId = document.getId();
        String kbId = document.getKbId();
        String defaultBucketName = s3Properties.getDefaultBucketName();
        HeadObjectResponse headObjectResponse = s3ClientUtils.headObject(defaultBucketName, filePath);
        FileContext fileContext = FileContext.from(headObjectResponse, defaultBucketName, filePath, documentId, kbId);
        // 将markdown的内容切分，进行embedding，再存入向量数据库中
        // 根据每页图片信息调用ocr模型，获取解析后的文本和元信息
        URL url = s3ClientUtils.generatePresignedUrl(defaultBucketName, filePath);
        fileContext.setFileUrl(url);
        asyncParseWord(fileContext);
    }

    @Transactional(rollbackFor = Exception.class)
    public CompletableFuture<?> asyncParseWord(FileContext fileContext) {
        // 1. 构建入参对象
        MarkItDownRequest request = new MarkItDownRequest();
        request.setUrl(fileContext.getFileUrl().toString());

        // 2. 调用异步方法（立即返回CompletableFuture，不阻塞主线程）
        CompletableFuture<ByteArrayInputStream> asyncFuture = asyncCallMarkItDownApi(request);

        // 3. 异步处理结果（回调函数，在异步线程执行）
        asyncFuture
                // 成功回调：处理解析结果（如保存到数据库、发送通知）
                .whenComplete((response, ex) -> {
                    if (ex != null) {
                        logger.error("调用markItDown服务异常：{}", ex.getMessage());
                    }


                    Path ragWord = null;
                    try {
                        ragWord = Files.createTempFile("rag_word", ".md");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    try (InputStream in = response) {

                        try (OutputStream out = Files.newOutputStream(ragWord)) {
                            in.transferTo(out);
                        }
                        // 上传到s3中
                        String fileId = FileIdGenerator.generateFileId(fileContext.getKey());
                        fileContext.setFileId(fileId);
                        String fileName = String.format("%s/%s%s", fileId, fileId, ".md");
                        s3ClientUtils.putObjectByTempFile(s3Properties.getDefaultBucketName(), fileName, ragWord, MediaType.TEXT_MARKDOWN_VALUE);
                        logger.info("word转md写入成功");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    // 将转换后的markdown进行分块处理
                    List<org.springframework.ai.document.Document> convert = convert(ragWord, fileContext);
                    logger.info("转换后的第一个Document：{}", convert.get(0).getText());
                    // 丰富存入向量库内容
                    List<org.springframework.ai.document.Document> convert1 = EsVectorDocumentConverter.convert(convert, fileContext);
                    vectorStore.add(convert1);
                    // 将文档标记为已处理
                    documentRepository.updateChunkNumAndStatusById(fileContext.getDocumentId(), convert.size(), String.valueOf(FileProcessStatusEnum.PROCESS_SUCCESS.getCode()));

                    // 移除临时文件
                    try {
                        Files.deleteIfExists(ragWord);
                    } catch (IOException e) {
                        logger.error("删除临时文件失败：" + e.getMessage());
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


    public List<org.springframework.ai.document.Document> convert(Path path, FileContext fileContext) {
        try {
            return loadMarkdownV2(path, fileContext);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * TODO对这个切分保持谨慎，需要测试看效果？？？ 我觉得不合适重新实现下
     * <p>
     * 切的方式不太一样，它把标题（#） 这种元素都切进了 metadata 中，除非你的系统能检测出 用户问题包含这些关键字，才能进行过滤和检索
     * 现在我更倾向于 。在块中包含 标题（#） 等这些信息，如果检索到将上下文给llm ，llm能自动的了解当前上下文的一个层次关系
     * <p>
     * 针对md 的图片，需要通过视觉模型进行图片理解，再将图片理解文本，进行embedding
     * 针对md 的代码部分，要尽量保证一个完整的代码片段 （代码片段上方或者下方一般也有解释说明，其实这块信息也应该放在一个块中比较合适）
     * 针对md 的表格，要尽量保证一个完整的表格 （表格上方或者下方一般都有解释说明，其实这块信息放在一个块中，是比较合适的。甚至可以让llm 输出一个 Summary(概要) 在语义检索时，能更精准的匹配，并且把概要和 表格内容，都在上下文中出现，让llm 生成的结果就包含了表格信息）
     *
     * @param path
     * @param fileContext
     * @return
     */
    public List<org.springframework.ai.document.Document> loadMarkdown(Path path, FileContext fileContext) {
        ArrayList<org.springframework.ai.document.Document> mdDocuments = new ArrayList<>();
        MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                // Markdown 中的水平规则将创建新的Document对象
                .withHorizontalRuleCreateDocument(true)
                .withIncludeCodeBlock(true)
                .withIncludeBlockquote(false)
                .build();
        MarkdownDocumentReader reader = new MarkdownDocumentReader(new FileSystemResource(path), config);
        List<org.springframework.ai.document.Document> documents = reader.get();
        for (int chunkNum = 0; chunkNum < documents.size(); chunkNum++) {
            org.springframework.ai.document.Document document = documents.get(chunkNum);
            DocumentMetadataMap documentMetadataMap = new DocumentMetadataMap(fileContext.getFileName(),
                    fileContext.getDocumentId(), fileContext.getFileId(), WORD_MIMETYPE,
                    true, String.valueOf(chunkNum), fileContext.getKbId());
            org.springframework.ai.document.Document documentNew = document.mutate().metadata(documentMetadataMap.toMap()).build();
            mdDocuments.add(documentNew);
        }
        return mdDocuments;
    }


    /**
     * 先按 标题拆分
     * 遇到代码 ，单拆，并包含包裹代码块的文本描述
     * 遇到表格 ，单拆，并包含包裹表格的文本描述
     * 遇到图片 ，单拆，将图片进行视觉模型的识别，并包含图片的描述
     *
     * @param path
     * @param fileContext
     * @return
     */
    public List<org.springframework.ai.document.Document> loadMarkdownV2(Path path, FileContext fileContext) throws IOException {
        RAGMarkdownSplitter ragMarkdownSplitter = new RAGMarkdownSplitter(fileContext);
        List<org.springframework.ai.document.Document> documents = ragMarkdownSplitter.splitForRAG(Files.readString(path, StandardCharsets.UTF_8));
        LinkedList<org.springframework.ai.document.Document> documentLinkedList = new LinkedList<>();
        for (int chunkNum = 0; chunkNum < documents.size(); chunkNum++) {
            org.springframework.ai.document.Document document = documents.get(chunkNum);
            String chunkContentType = (String) document.getMetadata().get(DocumentMetadataMap.CHUNK_CONTENT_TYPE);
            if (BlockType.TEXT.getType().equals(chunkContentType)) {
                LinkedList<org.springframework.ai.document.Document> convert = new LinkedList<>();
                convert.add(document);
                // 进行文本分割
                List<org.springframework.ai.document.Document> splitDocuments = textSplitterTool.splitCustomized(convert, 500, 200, 100, 10000, false);
                documentLinkedList.addAll(splitDocuments);
                continue;
            } else if (BlockType.IMAGE.getType().equals(chunkContentType)) {
                // TODO 针对img，需要调用视觉模型获取图片描述
                // 抽取图片内容是 url ，还是 base64 或者相对路径（处理不了）
                List<String> images = MdImageRegexExtractor.extractImages(document.getText());
                for (String image : images) {
                    if (image.startsWith("http") || image.startsWith("data:image")) {
                        Resource resource;
                        if (image.startsWith("data:image")) {
                            resource = Base64ImageToResourceUtil.convert(image);
                        } else {
                            UrlResource from = UrlResource.from(image);
                            byte[] contentAsByteArray = from.getContentAsByteArray();
                            resource = new ByteArrayResource(contentAsByteArray);
                        }
                        try {
                            String content = chatClientImage.prompt().user(u -> u.text("解释该图片描述的信息").media(MimeTypeUtils.IMAGE_JPEG, resource)).call().content();
                            // 将图片资源上传到s3中，元数据记录图片路径信息
                            String imagId = IdUtils.generateChunkImagId();
                            s3ClientUtils.putObjectByContentBytes(s3Properties.getDefaultBucketName(),
                                    fileContext.getFileId() + "/" + imagId + ".png",
                                    resource.getInputStream().readAllBytes(),
                                    S3FileType.JPEG.getMimeType());
                            document.getMetadata().put(DocumentMetadataMap.IMAGE_RESOURCE, imagId);
                            String textNew = MarkdownAllImageReplacer.replaceAllImages(document.getText(), "图片:[\n:" + content + "]\n");
                            org.springframework.ai.document.Document documentNew = document.mutate().text(textNew).build();
                            documentLinkedList.add(documentNew);
                        } catch (Exception e) {
                            logger.error("图片处理异常:{}", e.getMessage(), e);
                            documentLinkedList.add(document);
                        }
                    }
                }


            } else {
                // TODO 针对过于大的块，需要处理内容, 提取概要信息，将原始内容存放入元数据
                List<Integer> boxed = encoding.encode(document.getText()).boxed();
                if (boxed.size() > 3000) {
                    SystemMessage systemMessage = new SystemMessage("你是一名概要总结大师，你需要根据用户提供的内容（表格、代码块或图片描述），生成一份精准简洁的概要信息。请遵循以下规则：\n" +
                            "先明确内容类型：判断是表格、代码块还是图片，并给出主题定位（如 “产品价格对比表”“Java 字符串处理代码”“季度销售额折线图”）。\n" +
                            "提取核心信息：\n" +
                            "表格：提炼关键表头，总结数据核心结论（无需罗列全部数据）；\n" +
                            "代码块：说明开发语言、核心逻辑及关键输入输出；\n" +
                            "图片：若为图表，说明数据维度与趋势；若为示意图，描述核心元素与关系。\n" +
                            "格式要求：分 2 段呈现（第一段：类型与主题；第二段：核心信息与结论），总字数≤300 字，语言通俗，避免冗余术语");
                    UserMessage userMessage = new UserMessage("请基于你收到的规则，对以下内容生成概要：\n" + document.getText());
                    String response = chatModel.call(systemMessage, userMessage);
                    Map<String, Object> metadata = document.getMetadata();
                    metadata.put(DocumentMetadataMap.ORIGINAL_CONTENT, response);
                    document.mutate().text(response).metadata(metadata);
                    documentLinkedList.add(document);
                } else {
                    documentLinkedList.add(document);
                }
            }

        }

        return documentLinkedList;
    }


    /**
     * 异步调用版面解析接口
     *
     * @param request 入参对象
     * @return CompletableFuture<LayoutParsingResponse> 异步结果对象
     * 可通过 thenAccept()/thenApply() 处理成功结果，exceptionally() 处理异常
     */
    public CompletableFuture<ByteArrayInputStream> asyncCallMarkItDownApi(MarkItDownRequest request) {
        return webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.parseMediaType("text/markdown"))
                .body(Mono.just(request), MarkItDownRequest.class)
                .retrieve()
                // 1. 先将响应体转为 String（WebClient 支持 text/markdown 转 String）
                .bodyToMono(String.class)
                // 2. 将 String 转为 InputStream（指定 UTF-8 编码，避免乱码）
                .map(str -> new ByteArrayInputStream(str.getBytes(StandardCharsets.UTF_8)))
                // 异常处理：捕获HTTP错误（如4xx/5xx）
                .onErrorResume(WebClientResponseException.class, ex -> {
                    // 可根据状态码自定义异常信息
                    String errorMsg = String.format("API调用失败: %s, 状态码: %d",
                            ex.getResponseBodyAsString(), ex.getStatusCode().value());
                    return Mono.error(new RuntimeException(errorMsg, ex));
                })
                // 捕获其他异常（如网络超时）
                .onErrorResume(ex -> {
                    return Mono.error(new RuntimeException("请求处理异常: " + ex.getMessage(), ex));
                })
                .toFuture();
    }


}
