package com.gengzi.embedding.split;

import com.gengzi.context.DocumentMetadataMap;
import com.gengzi.context.FileContext;
import com.gengzi.enums.BlockType;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.markdown.*;
import org.springframework.ai.document.Document;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RAGMarkdownSplitter {
    // 特殊元素标记
    private static final String MARK_PREFIX = "###ZHLOVERAG#MARK#";
    private static final String MARK_SUFFIX = "###";
    private static final String regex = "###ZHLOVERAG#MARK#(\\d+)###";
    private static final Map<String, String> markTypeMap = new HashMap<>();
    // 上下文保留配置（可根据需求调整）
    private static final int SENTENCES_BEFORE = 2; // 特殊元素前保留2个句子
    private static final int SENTENCES_AFTER = 2;  // 特殊元素后保留2个句子
    private static final Pattern SENTENCE_SPLITTER = Pattern.compile("[。！？；,.!?;]\\s*"); // 句子分割符
    private static final Pattern SENTENCE_SPLITTER_LINE_BREAK = Pattern.compile("\\n+\\s*");
    // 支持GFM表格的解析器和渲染器
    private static final Parser PARSER = Parser.builder()
            .extensions(Arrays.asList(TablesExtension.create()))
            .build();
    // 自定义渲染器，不对markdown进行转义操作
    private static final MarkdownRenderer RENDERER = MarkdownRenderer.builder()
            .extensions(Arrays.asList(TablesExtension.create()))
            .nodeRendererFactory(new MarkdownNodeRendererFactory() {
                /**
                 * Create a new node renderer for the specified rendering context.
                 *
                 * @param context the context for rendering (normally passed on to the node renderer)
                 * @return a node renderer
                 */
                @Override
                public NodeRenderer create(MarkdownNodeRendererContext context) {
                    return new CustomNodeRenderer(context);
                }

                /**
                 * @return the additional special characters that this factory would like to have escaped in normal text; currently
                 * only ASCII characters are allowed
                 */
                @Override
                public Set<Character> getSpecialCharacters() {
                    return Set.of();
                }
            })
            .build();
    private static Pattern regEx = Pattern.compile(regex);
    private static int markCounter = 0;


    private FileContext fileContext;

    public RAGMarkdownSplitter(FileContext fileContext) {
        this.fileContext = fileContext;
    }

    /**
     * 给特殊元素添加标记（图片/表格/代码）
     */
    private static String addSpecialMarks(String original) {
        Node document = PARSER.parse(original);
        markCounter = 0;
        markTypeMap.clear();

        document.accept(new AbstractVisitor() {
            @Override
            public void visit(Image image) {
                wrapNodeWithMark(image, BlockType.IMAGE.getType());
                super.visit(image);
            }

            @Override
            public void visit(CustomBlock table) {
                if (table instanceof TableBlock) {
                    wrapNodeWithMark(table, BlockType.TABLE.getType());
                }

                super.visit(table);
            }

            @Override
            public void visit(FencedCodeBlock code) {
                wrapNodeWithMark(code, BlockType.CODE.getType());
                super.visit(code);
            }

            @Override
            public void visit(IndentedCodeBlock code) {
                wrapNodeWithMark(code,  BlockType.CODE.getType());
                super.visit(code);
            }

            private void wrapNodeWithMark(Node node, String type) {
                String mark = MARK_PREFIX + markCounter++ + MARK_SUFFIX;
                markTypeMap.put(mark, type);

                // 标记仅用于定位，不影响原文结构
                Text prefix = new Text("\n" + mark + "\n");
                node.insertBefore(prefix);
                Text suffix = new Text("\n" + mark + "\n");
                node.insertAfter(suffix);
            }
        });

        return RENDERER.render(document);
    }

    private static void splitByMarks(String marked, List<Chunk> chunks) {
        int positionCounter = 0;
        // 按照标记序号一个个处理
        List<String> sortedMarks = new ArrayList<>(markTypeMap.keySet());
        sortedMarks.sort(Comparator.comparingInt(m -> {
            Matcher matcher = regEx.matcher(m);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            } else {
                return Integer.MAX_VALUE;
            }
        }));

        for (String mark : sortedMarks) {

            int startMarkPos = marked.indexOf(mark);
            int endMarkPos = marked.indexOf(mark, startMarkPos + mark.length());

            // 关键修复2：如果仍找不到标记，跳过并打印错误
            if (startMarkPos == -1 || endMarkPos == -1) {
                throw new RuntimeException("无法匹配到对应分隔标记");

            }

            // 计算偏移量（修正标记导致的位置偏差）
//            int offset = calculateOffset(marked, startMarkPos, mark);
            int originalStart = Math.max(0, startMarkPos);
            int originalEnd = Math.min(marked.length(), endMarkPos + mark.length());


            // 提前标记前内容为一个chunk块
            if (originalStart > 0) {
                String before = extractContent(marked, 0, originalStart);
                chunks.add(new Chunk(before.trim(), "TEXT", positionCounter++));
            }


            // 提取前上文
            String beforeContent = extractSentences(marked, 0, originalStart, SENTENCES_BEFORE, true);
            // 获取截断块内容
            String content = extractContent(marked, originalStart + mark.length(), originalEnd - mark.length());
            // 获取后下文
            String afterContent = extractSentences(marked, originalEnd, marked.length(), SENTENCES_AFTER, false);
            // 移除上文中存在截断的内容
            beforeContent = checkMarkIsExist(sortedMarks, beforeContent, true);
            // 移除下文中存在截断的内容
            afterContent = checkMarkIsExist(sortedMarks, afterContent, false);


            if (!beforeContent.isEmpty() || !afterContent.isEmpty()) {
                String chunkContent = String.join("\n", beforeContent, content, afterContent).trim();
                chunks.add(new Chunk(chunkContent, markTypeMap.get(mark), positionCounter++));
            }

            marked = marked.substring(originalEnd);
        }
    }

    public static String removeEmptyLines(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        // 正则说明：
        // ^\\s*$ 匹配空行（^ 行首，$ 行尾，\\s* 零个或多个空白字符）
        // \\r?\\n 匹配换行符（Windows \r\n 或 Linux \n）
        // + 表示连续空行合并处理
        // 替换为单个换行符，避免多空行替换后出现内容粘连
        return str.replaceAll("(?m)^\\s*\\r?\\n+", "");
    }

    // 提取上下文方法调整为按段落分割
    private static String extractSentencesByLine(String text, int rangeStart, int rangeEnd, int count, boolean isBefore) {
        if (count <= 0 || rangeStart >= rangeEnd) return "";
        String segment = text.substring(rangeStart, rangeEnd);
        // 按换行分割为段落数组
        String[] paragraphs = SENTENCE_SPLITTER_LINE_BREAK.split(segment);
        List<String> validParagraphs = new ArrayList<>();
        for (String p : paragraphs) {
            if (!p.trim().isEmpty()) {
                validParagraphs.add(p.trim()); // 过滤空段落
            }
        }

        if (isBefore) {
            // 取末尾的count个段落
            int start = Math.max(0, validParagraphs.size() - count);
            return String.join("\n", validParagraphs.subList(start, validParagraphs.size()));
        } else {
            // 取开头的count个段落
            int end = Math.min(count, validParagraphs.size());
            return String.join("\n", validParagraphs.subList(0, end));
        }
    }

    private static String checkMarkIsExist(List<String> sortedMarks, String content, boolean isBefore) {
        for (String markCheck : sortedMarks) {
            // 命中上文或者下文的句子存在截断标志，将截断标志和后面的内容都清除
            boolean contains = content.contains(markCheck);
            if (!contains) {
                continue;
            }
            int markCheckIndex = content.indexOf(markCheck);
            if (isBefore) {
                content = content.substring(markCheckIndex + markCheck.length(), content.length());
            } else {
                content = content.substring(0, markCheckIndex);
            }
        }
        return content;
    }

    private static String extractContent(String marked, int start, int end) {
        if (start >= end) {
            return "";
        }
        return marked.substring(start, end);
    }

    /**
     * @param marked  原文
     * @param upToPos 截止位置
     * @param mark    标记
     * @return
     */
    private static int calculateOffset(String marked, int upToPos, String mark) {
        // 开头到标记处
        String sub = marked.substring(0, upToPos);
        int offset = 0;
        for (String m : markTypeMap.keySet()) {
            if (m.equals(mark)) continue;
            String escapedM = m.replace("_", "\\_");
            // 同时统计原标记和转义标记的出现次数
            int count = countOccurrences(sub, m) + countOccurrences(sub, escapedM);
            // 转义标记比原标记长（多了转义符\），按实际长度计算偏移
            offset += countOccurrences(sub, m) * m.length();
            offset += countOccurrences(sub, escapedM) * escapedM.length();
        }
        return offset;
    }

    private static int countOccurrences(String text, String target) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(target, index)) != -1) {
            count++;
            index += target.length();
        }
        return count;
    }

    // 修正偏移量计算（考虑转义符的长度）

    private static String extractSentences(String text, int rangeStart, int rangeEnd, int count, boolean isBefore) {
        if (count <= 0 || rangeStart >= rangeEnd) return "";
        String segment = text.substring(rangeStart, rangeEnd);
        List<String> sentences = new ArrayList<>(Arrays.asList(SENTENCE_SPLITTER.split(segment)));
        sentences.removeIf(s -> s.trim().isEmpty());

        if (isBefore) {
            int start = Math.max(0, sentences.size() - count);
            return String.join("。", sentences.subList(start, sentences.size())) + (sentences.size() > 0 ? "。" : "");
        } else {
            int end = Math.min(count, sentences.size());
            return String.join("。", sentences.subList(0, end)) + (sentences.size() > 0 ? "。" : "");
        }
    }

    /**
     * 核心方法：为RAG系统分割上下文，仅保留特殊元素前后的小段内容
     */
    public List<Document> splitForRAG(String markdown) {
        List<Document> chunks = new ArrayList<>();
        if (markdown == null || markdown.isEmpty()) return chunks;

        // 步骤1：给特殊元素添加标记
        String markedMarkdown = addSpecialMarks(markdown);

        // 步骤2：按标记分割，提取前后小段上下文
        splitByMarksByLine(markedMarkdown, chunks);

        return chunks;
    }

    private void splitByMarksByLine(String marked, List<Document> chunks) {
        int positionCounter = 0;
        // 按照标记序号一个个处理
        List<String> sortedMarks = new ArrayList<>(markTypeMap.keySet());
        sortedMarks.sort(Comparator.comparingInt(m -> {
            Matcher matcher = regEx.matcher(m);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            } else {
                return Integer.MAX_VALUE;
            }
        }));

        for (String mark : sortedMarks) {

            int startMarkPos = marked.indexOf(mark);
            int endMarkPos = marked.indexOf(mark, startMarkPos + mark.length());

            // 关键修复2：如果仍找不到标记，跳过并打印错误
            if (startMarkPos == -1 || endMarkPos == -1) {
                throw new RuntimeException("无法匹配到对应分隔标记");

            }

            // 计算偏移量（修正标记导致的位置偏差）
//            int offset = calculateOffset(marked, startMarkPos, mark);
            int originalStart = Math.max(0, startMarkPos);
            int originalEnd = Math.min(marked.length(), endMarkPos + mark.length());

            // 提取前上文
            String beforeContent = extractSentencesByLine(marked, 0, originalStart, SENTENCES_BEFORE, true);
            // 获取截断块内容
            String content = extractContent(marked, originalStart + mark.length(), originalEnd - mark.length());
            // 获取后下文
            String afterContent = extractSentencesByLine(marked, originalEnd, marked.length(), SENTENCES_AFTER, false);
            // 移除上文中存在截断的内容
            beforeContent = checkMarkIsExist(sortedMarks, beforeContent, true);
            // 移除下文中存在截断的内容
            afterContent = checkMarkIsExist(sortedMarks, afterContent, false);

            // 提前标记前内容为一个chunk块
            if (originalStart > 0) {
                String before = extractContent(marked, 0, originalStart);

                if (!before.isBlank() && !beforeContent.contains(removeEmptyLines(before.trim()))) {
                    DocumentMetadataMap documentMetadataMap = new DocumentMetadataMap(fileContext.getFileName(), fileContext.getDocumentId(),
                            fileContext.getFileId(), fileContext.getContentType(),
                            true, String.valueOf(0), fileContext.getKbId());
                    documentMetadataMap.setChunkContentType(BlockType.TEXT.getType());
                    Document document = new Document(before.trim(), documentMetadataMap.toMap());
//                    chunks.add(new Chunk(before.trim(), "TEXT", positionCounter++));
                    chunks.add(document);
                }
            }

            if (!beforeContent.isEmpty() || !afterContent.isEmpty()) {
                String chunkContent = String.join("\n", beforeContent, content, afterContent).trim();
                DocumentMetadataMap documentMetadataMap = new DocumentMetadataMap(fileContext.getFileName(), fileContext.getDocumentId(),
                        fileContext.getFileId(), fileContext.getContentType(),
                        true, String.valueOf(0), fileContext.getKbId());
                documentMetadataMap.setChunkContentType(markTypeMap.get(mark));
                Document document = new Document(chunkContent, documentMetadataMap.toMap());
                chunks.add(document);
//                chunks.add(new Chunk(chunkContent, markTypeMap.get(mark), positionCounter++));
            }

            marked = marked.substring(originalEnd);
        }
        if (!marked.isEmpty()) {
            DocumentMetadataMap documentMetadataMap = new DocumentMetadataMap(fileContext.getFileName(), fileContext.getDocumentId(),
                    fileContext.getFileId(), fileContext.getContentType(),
                    true, String.valueOf(0), fileContext.getKbId());
            documentMetadataMap.setChunkContentType(BlockType.TEXT.getType());
            Document document = new Document(marked.trim(), documentMetadataMap.toMap());
            chunks.add(document);
        }
    }

    // 自定义节点渲染器：重写 Text 节点渲染，禁用转义
    private static class CustomNodeRenderer extends CoreMarkdownNodeRenderer {

        public CustomNodeRenderer(MarkdownNodeRendererContext context) {
            super(context);
        }

        @Override
        public void visit(Text text) {
            MarkdownWriter writer = this.context.getWriter();
            // 直接写入文本原内容，不调用父类的转义方法
            writer.text(text.getLiteral(), null);
        }
    }

    // 块信息封装（仅包含上下文）
    static class Chunk {
        String content;    // 上下文内容（特殊元素前后的片段）
        String relatedType;// 关联的特殊元素类型（image/table/code）
        int position;      // 顺序位置

        public Chunk(String content, String relatedType, int position) {
            this.content = content;
            this.relatedType = relatedType;
            this.position = position;
        }
    }
}