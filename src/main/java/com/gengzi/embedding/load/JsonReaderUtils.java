package com.gengzi.embedding.load;


import org.springframework.ai.document.Document;
import org.springframework.ai.reader.JsonReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JsonReaderUtils {


    @Value("classpath:jsonfile/jsonReader.json")
    private Resource resource;

    public List<Document> loadJsonAsDocuments() {
        JsonReader jsonReader = new JsonReader(resource, "description", "content");
        return jsonReader.get();
    }


}
