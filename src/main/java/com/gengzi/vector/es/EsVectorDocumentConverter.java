package com.gengzi.vector.es;


import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import com.gengzi.context.DocumentMetadataMap;
import com.gengzi.context.FileContext;
import com.gengzi.utils.FileIdGenerator;
import com.gengzi.utils.HanLPUtil;
import com.gengzi.utils.InstantConverter;
import com.gengzi.utils.PunctuationAndLineBreakRemover;
import com.gengzi.vector.es.document.ExtendedDocument;
import org.springframework.ai.document.Document;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 将Document 转换为 EsVectorDocument
 */
public class EsVectorDocumentConverter {


    public static List<Document> convert(List<Document> documents, FileContext fileContext) {

        if (documents == null && documents.isEmpty()) {
            throw new IllegalArgumentException("documents cannot be null or empty");
        }
        String fileId = FileIdGenerator.generateFileId(fileContext.getKey());
        ArrayList<Document> esVectorDocuments = new ArrayList<>(documents.size());
        for (int chunkNumber = 0; chunkNumber < documents.size(); chunkNumber++) {
            // 生成每个文本块（chunkid）的id  （docid+"_"+chunknumber）

            Document document = documents.get(chunkNumber);
            EsVectorDocument esVectorDocument = new EsVectorDocument();
            esVectorDocument.setChunkId(String.format("%s_%d", fileId, chunkNumber));
            esVectorDocument.setContent(document.getText());
            esVectorDocument.setMetadata(document.getMetadata());
            esVectorDocument.setDocId(fileContext.getDocumentId());
            esVectorDocument.setFId(fileId);
            esVectorDocument.setAvailableInt(1);
            esVectorDocument.setCreateTime(InstantConverter.instantToString(fileContext.getLastModified(), ZoneId.systemDefault()));
            esVectorDocument.setCreateTimestampFlt(fileContext.getLastModified().toEpochMilli());
            // spring ai 只能处理一个document，不能跨document处理
            esVectorDocument.setPageNumInt((String) document.getMetadata().get(DocumentMetadataMap.PAGE_RANGE));
//            esVectorDocument.setImgId(String.format("%s_%d", fileId, chunkNumber));
            // 分词需要移除各种格式
            String content = PunctuationAndLineBreakRemover.removePunctuationAndAllWhitespace(document.getText());
            String title = PunctuationAndLineBreakRemover.removePunctuationAndAllWhitespace(fileContext.getFileName());
            esVectorDocument.setContentLtks(HanLPUtil.nShortSegment(content).stream().collect(Collectors.joining(" ")));
            esVectorDocument.setContentSmLtks(HanLPUtil.segment(content).stream().collect(Collectors.joining(" ")));
            esVectorDocument.setTitleSmTks(HanLPUtil.segment(title).stream().collect(Collectors.joining(" ")));
            esVectorDocument.setTitleTks(HanLPUtil.nShortSegment(title).stream().collect(Collectors.joining(" ")));

            ExtendedDocument extendedDocument = new ExtendedDocument(esVectorDocument);

            esVectorDocuments.add(extendedDocument);
        }
        return esVectorDocuments;
    }


}
