import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.markdown.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RAGMarkdownSplitter {
    // 特殊元素标记
    private static final String MARK_PREFIX = "###RAG#MARK#";
    private static final String MARK_SUFFIX = "###";
    private static final String regex = "###RAG#MARK#(\\d+)###";
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

    /**
     * 核心方法：为RAG系统分割上下文，仅保留特殊元素前后的小段内容
     */
    public static List<Chunk> splitForRAG(String markdown) {
        List<Chunk> chunks = new ArrayList<>();
        if (markdown == null || markdown.isEmpty()) return chunks;

        // 步骤1：给特殊元素添加标记
        String markedMarkdown = addSpecialMarks(markdown);

        // 步骤2：按标记分割，提取前后小段上下文
//        splitByMarks(markedMarkdown, chunks);

        splitByMarksByLine(markedMarkdown, chunks);

        return chunks;
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
                wrapNodeWithMark(image, "image");
                super.visit(image);
            }

            @Override
            public void visit(CustomBlock table) {
                if (table instanceof TableBlock) {
                    wrapNodeWithMark(table, "table");
                }

                super.visit(table);
            }

            @Override
            public void visit(FencedCodeBlock code) {
                wrapNodeWithMark(code, "code");
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

    private static void splitByMarksByLine(String marked, List<Chunk> chunks) {
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
                    chunks.add(new Chunk(before.trim(), "TEXT", positionCounter++));
                }
            }

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

    // 修正偏移量计算（考虑转义符的长度）

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

    // 测试方法
    public static void main(String[] args) {
        String testMd = """
                # Markdown 全元素示例文档
                
                本文档涵盖 Markdown 常用元素，可直接复制使用，帮助快速掌握各类格式的编写方法。
                
                ------
                
                ## 一、标题层级
                
                Markdown 支持 6 级标题，通过 `#` 数量区分，`#` 越多级别越低。
                
                - `# 一级标题` → 对应 HTML 的 `<h1>`
                - `## 二级标题` → 对应 HTML 的 `<h2>`
                - `### 三级标题` → 对应 HTML 的 `<h3>`
                - `#### 四级标题` → 对应 HTML 的 `<h4>`
                - `##### 五级标题` → 对应 HTML 的 `<h5>`
                - `###### 六级标题` → 对应 HTML 的 `<h6>`
                
                ------
                
                ## 二、文本格式
                
                用于突出或区分文本内容，常见格式如下：
                
                - **加粗文本**：用 `**` 包裹，例如 `**这是加粗文本**`
                - *斜体文本*：用 `*` 包裹，例如 `*这是斜体文本*`
                - ***加粗斜体文本***：用 `***` 包裹，例如 `***这是加粗斜体文本***`
                - ~~删除线文本~~：用 `~~` 包裹，例如 `~~这是删除线文本~~`
                - 下划线文本：用 `<u>` 标签包裹，例如 `<u>这是下划线文本</u>`
                - `行内代码`：用 ``` 包裹，例如 ``print("Hello World")``
                
                ------
                
                ## 三、引用
                
                用于引用外部内容或强调特定段落，支持嵌套。
                
                1. 基础引用：用 `>` 开头
                
                > 这是一级引用，常用于引用他人观点或文献内容。
                
                1. 嵌套引用：在一级引用内加 `>>`
                
                > 这是一级引用
                >
                >\s
                >
                > > 这是二级嵌套引用
                > >
                > >\s
                > >
                > > > 这是三级嵌套引用
                
                ------
                
                ## 四、列表
                
                分为有序列表和无序列表，支持嵌套使用。
                
                ### 1. 无序列表
                
                用 `-`、`+` 或 `*` 开头，三者效果一致。
                
                - 无序列表项 1
                - 无序列表项 2
                  - 嵌套无序列表项 2.1
                  - 嵌套无序列表项 2.2
                - 无序列表项 3
                
                ### 2. 有序列表
                
                用数字 + `.` 开头，数字顺序不影响显示结果（最终会自动排序）。
                
                1. 有序列表项 1
                2. 有序列表项 2
                   1. 嵌套有序列表项 2.1
                   2. 嵌套有序列表项 2.2
                3. 有序列表项 3
                
                ------
                
                ## 五、代码块
                
                用于展示多行代码，支持指定编程语言以实现语法高亮。
                
                ### 1. 基础代码块
                
                用 3 个 ``` 包裹，不指定语言时无语法高亮。
                
                ```plaintext
                # 这是基础代码块，无语法高亮
                def hello():
                    print("Hello Markdown")
                hello()
                ```
                
                ### 2. 带语法高亮的代码块
                
                在开头的 3 个 ``` 后指定编程语言（如 `python`、`java`、`html`）。
                
                ```python
                # 这是 Python 语法高亮代码块
                def calculate_sum(a: int, b: int) -> int:
                    ""\"计算两个整数的和""\"
                    return a + b
                
                result = calculate_sum(5, 3)
                print(f"结果：{result}")  # 输出：结果：8
                ```
                
                ------
                
                ## 六、链接
                
                分为普通链接、锚点链接和图片链接三类。
                
                ### 1. 普通链接
                
                格式：`[链接文本](链接地址 "可选的提示文本")`
                
                - [Markdown 官方文档](https://daringfireball.net/projects/markdown/)
                - [GitHub](https://github.com/)
                
                ### 2. 锚点链接
                
                用于跳转到文档内指定标题，格式：`[链接文本](#标题内容)`（标题内容需与目标标题完全一致，不区分大小写）。
                
                - [跳转到 “表格” 部分](https://www.doubao.com/chat/25769966519370242#七、表格)
                - [跳转到 “分割线” 部分](https://www.doubao.com/chat/25769966519370242#八、分割线)
                
                ### 3. 图片链接
                
                格式：`![图片加载失败时的提示文本](图片地址 "鼠标悬停时的提示文本")`
                
                - 本地图片：`![示例图片](./images/sample.jpg "本地示例图片")`
                
                - 网络图片：
                
                  ![img](data:image/svg+xml,%3csvg%20xmlns=%27http://www.w3.org/2000/svg%27%20version=%271.1%27%20width=%27400%27%20height=%27256%27/%3e)
                
                  ![image](https://github.githubassets.com/images/guide/logo_text.svg)
                
                 \s
                
                ------
                
                ## 七、表格
                
                支持表头、对齐方式设置，用 `|` 分隔列，`-` 分隔表头与内容。
                
                ### 1. 基础表格
                
                | 姓名 | 年龄 | 职业     |
                | ---- | ---- | -------- |
                | 张三 | 25   | 程序员   |
                | 李四 | 30   | 产品经理 |
                
                ### 2. 带对齐方式的表格
                
                在 `-` 后加 `:` 控制对齐，`:` 在左侧为左对齐，右侧为右对齐，两侧都有为居中对齐。
                
                | 左对齐列 | 居中对齐列 | 右对齐列 |
                | -------- | ---------- | -------- |
                | 内容 1   | 内容 A     | 100      |
                | 内容 2   | 内容 B     | 200      |
                
                ------
                
                ## 八、分割线
                
                用 3 个及以上的 `-`、`*` 或 `_` 实现，单独占一行，前后需空行。
                
                ------
                
                ------
                
                ------
                
                ------
                
                ## 九、任务列表
                
                用 `- [ ]` 表示未完成，`- [x]` 表示已完成，支持嵌套。
                
                -  完成 Markdown 基础元素学习
                -  编写示例文档
                -  练习表格与代码块使用
                  -  完成基础表格练习
                  -  完成语法高亮代码块练习
                """;
        List<Chunk> chunks = splitForRAG(testMd);
        for (Chunk chunk : chunks) {
            System.out.println("Content: " + chunk.content);
            System.out.println("Related Type: " + chunk.relatedType);
            System.out.println("Position: " + chunk.position);
            System.out.println("-------------------------");
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
//            super.visit(text);
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