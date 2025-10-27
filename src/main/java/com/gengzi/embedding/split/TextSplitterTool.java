package com.gengzi.embedding.split;


import dev.langchain4j.data.document.Metadata;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TextSplitterTool {


    /**
     * spring ai 默认分词器
     * 1. 它使用 CL100K_BASE 编码将输入文本编码为标记。
     * 2. 它根据将编码文本拆分成块defaultChunkSize。
     * 3. 对于每个块：
     * * 它将块解码回文本。
     * * 它尝试在 之后找到合适的断点（句号、问号、感叹号或换行符）minChunkSizeChars。
     * * 如果发现断点，它会截断该点处的块。
     * * 它会修剪块并根据设置选择性地删除换行符keepSeparator。
     * * 如果结果块长于minChunkLengthToEmbed，则将其添加到输出中。
     * 4. 此过程持续进行，直到所有令牌都被处理或maxNumChunks达到。
     * 5. 如果剩余文本的长度超过，则会将其添加为最终块minChunkLengthToEmbed。
     *
     * @param documents
     * @return
     */
    public List<Document> splitDocuments(List<Document> documents) {
        TokenTextSplitter splitter = new TokenTextSplitter();
        return splitter.apply(documents);
    }

    /**
     *
     * @param documents
     * @param chunkSize  每个文本块的目标大小（以标记为单位）（默认值：800）。
     * @param minChunkSizeChars  每个文本块的最小字符数（默认值：350）
     * @param minChunkLengthToEmbed 要包含的块的最小长度（默认值：5）。
     * @param maxNumChunks 从文本生成的最大块数（默认值：10000）。
     * @param keepSeparator 是否在块中保留分隔符（如换行符）（默认值：true）。
     * @return
     */
    public List<Document> splitCustomized(List<Document> documents, int chunkSize,
                                          int minChunkSizeChars, int minChunkLengthToEmbed,
                                          int maxNumChunks, boolean keepSeparator) {
        TokenTextSplitter splitter = new TokenTextSplitter(chunkSize, minChunkSizeChars, minChunkLengthToEmbed, maxNumChunks, keepSeparator);
        return splitter.apply(documents);
    }











}
