//package com.gengzi.embedding.split;
//
//
//import dev.langchain4j.data.document.DocumentSplitter;
//import dev.langchain4j.data.document.Metadata;
//import dev.langchain4j.data.document.splitter.DocumentSplitters;
//import dev.langchain4j.data.message.ChatMessage;
//import dev.langchain4j.data.segment.TextSegment;
//import dev.langchain4j.model.TokenCountEstimator;
//import org.springframework.ai.document.Document;
//
//import java.util.List;
//
//public class LangChain4jTextSpliter {
//
//
//    public List<Document> splitMdoCustomized(List<Document> documents) {
//
//        // 2. 创建文档对象（可添加元数据）
//        Metadata metadata = Metadata.from("source", "ai_intro.md");
//        dev.langchain4j.data.document.Document from = dev.langchain4j.data.document.Document.from("", metadata);
//
//
//        // 3. 初始化MarkdownTextSplitter
//        // 参数1：每个片段的最大长度（按字符数）
//        // 参数2：片段间的重叠长度
//        DocumentSplitter documentSplitter = create(200, 20);
//
//        // 4. 执行分割
//        List<TextSegment> segments = documentSplitter.split(from);
//
//        // 5. 输出分割结果
//        System.out.println("分割后的片段数量：" + segments.size() + "\n");
//        for (int i = 0; i < segments.size(); i++) {
//            TextSegment segment = segments.get(i);
//            System.out.println("片段 " + (i + 1) + "：");
//            System.out.println("内容：" + segment.text());
//            System.out.println("元数据：" + segment.metadata());
//            System.out.println("------------------------");
//        }
//        return null;
//    }
//
//
//    public static DocumentSplitter create(int maxSegmentLength, int overlap) {
//        // 定义Markdown特有的分隔符（优先级从高到低）
//        // 1. 标题（# 到 ######）：确保标题与内容绑定
//        // 2. 代码块（```开头和结尾）：保持代码块完整
//        // 3. 水平分隔线（---、***、___）：分割不同章节
//        // 4. 列表项（- 或 数字. 开头）：保持列表结构
//        // 5. 段落（空行）：保持段落完整性
//        // 6. 句子（标点符号）：细粒度语义单元
//        // 7. 单词（空格）：最后降级方案
//        String[] markdownSeparators = {
//                "(?m)^#{1,6} ",          // 标题（# 到 ###### 开头）
//                "```\\n", "```",        // 代码块（```包裹）
//                "(?m)^---$", "(?m)^***$", "(?m)^___$",  // 水平分隔线
//                "(?m)^- ", "(?m)^\\d+\\. ",  // 列表项（- 或 数字. 开头）
//                "\n\n",                 // 段落（空行）
//                "\\. ", "! ", "? ",     // 句子（句号、感叹号、问号结尾）
//                " "                     // 单词（空格）
//        };
//
////        DocumentSplitter
////        // 构建递归分割器，按上述分隔符优先级分割
//        return DocumentSplitters.recursive(
//                maxSegmentLength,  // 最大片段长度
//                overlap           // 重叠长度
//
//        );
//
//    }
//
//}
