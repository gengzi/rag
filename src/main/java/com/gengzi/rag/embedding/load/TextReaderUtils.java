//package com.gengzi.embedding.load;
//
//
//import org.springframework.ai.document.Document;
//import org.springframework.ai.reader.TextReader;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.core.io.Resource;
//import org.springframework.stereotype.Component;
//
//import java.util.List;
//
//@Component
//public class TextReaderUtils {
//
//
//    private final Resource resource;
//
//    TextReaderUtils(@Value("classpath:textfile/text-source.txt") Resource resource) {
//        this.resource = resource;
//    }
//
//    public List<Document> loadText() {
//        TextReader textReader = new TextReader(this.resource);
//        textReader.getCustomMetadata().put("filename", "text-source.txt");
//        return textReader.read();
//    }
//}
