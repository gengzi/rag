package com.gengzi.vector.es.document;

import com.gengzi.vector.es.EsVectorDocument;
import org.springframework.ai.document.Document;


/**
 * 继承自 Spring AI 的 Document 类，用于扩展文档
 * 添加了自定义字段，如向量、元数据等
 */
public class ExtendedDocument extends Document {


    private EsVectorDocument esVectorDocument;

    public ExtendedDocument(EsVectorDocument esVectorDocument) {
        super(esVectorDocument.getChunkId(), esVectorDocument.getContent(),
                esVectorDocument.getMetadata());
        this.esVectorDocument = esVectorDocument;
    }

    public EsVectorDocument getEsVectorDocument() {
        return esVectorDocument;
    }

    public void setEsVectorDocument(EsVectorDocument esVectorDocument) {
        this.esVectorDocument = esVectorDocument;
    }
}
