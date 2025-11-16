package com.gengzi.entity;


import org.springframework.ai.document.Document;


public class MyOpenSearchDocument extends Document {

    private OpenSearchExpandDocument openSearchExpandDocument;

    public MyOpenSearchDocument(OpenSearchExpandDocument openSearchExpandDocument) {
        super(openSearchExpandDocument.getId(), openSearchExpandDocument.getContent(),
                openSearchExpandDocument.getMetadata());
        this.openSearchExpandDocument = openSearchExpandDocument;
    }

    public OpenSearchExpandDocument getOpenSearchExpandDocument() {
        return openSearchExpandDocument;
    }
}
