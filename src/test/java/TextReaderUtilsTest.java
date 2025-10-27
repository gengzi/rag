import com.gengzi.RagServerApplication;
import com.gengzi.embedding.load.TextReaderUtils;
import com.gengzi.embedding.split.TextSplitterTool;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest(classes = RagServerApplication.class)
public class TextReaderUtilsTest {

    @Autowired
    private TextReaderUtils textReaderUtils;


    @Autowired
    private TextSplitterTool textSplitterTool;
//
//    @Autowired
//    private VectorStore vectorStore;

    @Test
    void testLoadJsonAsDocuments() {
//        List<Document> documents = textReaderUtils.loadText();
//        documents = textSplitterTool.splitDocuments(documents);
//        vectorStore.add(documents);
//        List<Document> results = this.vectorStore.similaritySearch(SearchRequest.builder().query("Qwen3").topK(5).build());
//        System.out.println("results" + results);
    }


}
