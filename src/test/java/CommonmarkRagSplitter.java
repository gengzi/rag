import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.ModelType;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 基于 commonmark-java 的 RAG 分块工具
 * 特性：语法边界优先（标题/代码块/表格/图片独立拆分）+ Token 长度控制
 */
public class CommonmarkRagSplitter {
    // 配置参数（适配 GPT-3.5 4k Token）
    private final int MAX_TOKEN_PER_CHUNK; // 单个 chunk 最大 Token 数
    private final int CHUNK_OVERLAP;       // 相邻 chunk 重叠 Token 数
    private final Encoding tokenEncoder;   // Token 编码器
    private final Parser markdownParser;   // Markdown 解析器（带扩展）
    private final List<Chunk> chunkResult = new ArrayList<>();
    private final StringBuilder tempContent = new StringBuilder(); // 临时拼接普通内容
    // 临时状态
    private String currentChapter = "根文档"; // 当前章节标题


    // 构造器：初始化配置
    public CommonmarkRagSplitter(int maxTokenPerChunk, int chunkOverlap) {
        this.MAX_TOKEN_PER_CHUNK = maxTokenPerChunk;
        this.CHUNK_OVERLAP = chunkOverlap;
        // 初始化 Token 编码器（GPT-3.5/4 兼容）
        this.tokenEncoder = Encodings.newDefaultEncodingRegistry().getEncodingForModel(ModelType.GPT_3_5_TURBO);
        // 初始化解析器（启用表格、GFM图片扩展）
        this.markdownParser = Parser.builder()
                .extensions(Arrays.asList(
                        TablesExtension.create()
                ))
                .build();
    }

    // ------------------------------ 测试示例 ------------------------------
    public static void main(String[] args) {
        String testMd = """
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

        CommonmarkRagSplitter commonmarkRagSplitter = new CommonmarkRagSplitter(300, 10);
        List<Chunk> split = commonmarkRagSplitter.split(testMd);
        for (Chunk chunk : split) {
            System.out.println("分块：" + chunk.getType() + ": " + chunk.getContent() + "\n\n");
            System.out.println("---------------------------------------------------");
        }

    }


    // ------------------------------ 节点处理逻辑 ------------------------------

    /**
     * 核心方法：拆分 Markdown 文本为 RAG 可用的 chunk
     */
    public List<Chunk> split(String markdownContent) {
        // 预处理：统一换行符，重置临时状态
        String content = markdownContent.replace("\r\n", "\n").replace("\r", "\n");
        resetTempState();
        // 解析为 AST
        Node document = markdownParser.parse(content);



        // 遍历 AST 节点，按语法边界分块
        document.accept(new AbstractVisitor() {
//            /**
//             * 针对标题节点的处理
//             * @param heading
//             */
//            @Override
//            public void visit(Heading heading) {
//                handleHeading(heading);
//                super.visit(heading);
//            }

            /**
             * 针对围栏代码块
             * 围栏代码块是 Markdown 中用于表示多行代码的语法，通常用 3 个反引号（```） 或 3 个波浪线（~~~） 作为开始和结束标记
             * @param codeBlock
             */
            @Override
            public void visit(FencedCodeBlock codeBlock) {
                handleCodeBlock(codeBlock);
                super.visit(codeBlock);
            }

            /**
             * 针对表格的处理
             * @param table
             */
            @Override
            public void visit(CustomBlock table) {
                if (table instanceof TableBlock) {
                    handleTable((TableBlock) table);
                }
                super.visit(table);
            }


//            /**
//             * 针对行内代码的处理
//             * @param code
//             */
//            @Override
//            public void visit(Code code) {
//                handleCode(code);
//                super.visit(code);
//            }

            /**
             * 针对image 的处理
             * @param image
             */
            @Override
            public void visit(Image image) {
                handleImage(image);
                super.visit(image);
            }

//            /**
//             * 针对段落的处理
//             * @param paragraph
//             */
//            @Override
//            public void visit(Paragraph paragraph) {
//                handleParagraph(paragraph);
//                super.visit(paragraph);
//            }

//            /**
//             * 针对无序或者有序列表
//             * @param listItem
//             */
//            @Override
//            public void visit(ListItem listItem) {
//                handleListItem(listItem);
//                super.visit(listItem);
//            }
        });

        // 保存最后一段临时内容
        saveTempContent();

        return chunkResult;
    }

    /**
     * 处理标题：更新章节，保存临时内容
     */
    private void handleHeading(Heading heading) {
        //saveTempContent(); // 先保存之前的普通内容

        // 提取标题文本和层级
        String headingText = getNodeText(heading);
        int level = heading.getLevel();
        // 更新章节（如 "根文档 > 1. 功能 > 1.1 介绍"）
        updateChapter(headingText, level);

        // 标题单独分块
        String content = "#".repeat(level) + " " + headingText;
        tempContent.append(content).append("\n");
//        addChunk(ChunkType.HEADING, content);
    }

    /**
     * 处理代码块：强制单独分块
     */
    private void handleCodeBlock(FencedCodeBlock codeBlock) {
        saveTempContent();

        String lang = codeBlock.getInfo() != null ? codeBlock.getInfo() : "unknown";
        String code = codeBlock.getLiteral();
        String content = "```" + lang + "\n" + code + "\n```";

        addChunk(ChunkType.CODE_BLOCK, content, "语言：" + lang);
    }

    /**
     * 处理表格：强制单独分块
     */
    private void handleTable(TableBlock table) {
        saveTempContent();

        String tableContent = getNodeText(table); // 保留表格 Markdown 语法
        addChunk(ChunkType.TABLE, tableContent);
    }

    /**
     * 处理图片：单独分块
     */
    private void handleImage(Image image) {
        saveTempContent();

//        String alt = image.getAlt() != null ? image.getAlt() : "";
        String url = image.getDestination();
        String title = image.getTitle();
        String content = "![" + title + "](" + url + ")";

        addChunk(ChunkType.IMAGE, content, "链接：" + url);
    }

    /**
     * 处理段落：临时拼接，后续长度控制
     */
    private void handleParagraph(Paragraph paragraph) {
        String paraText = getNodeText(paragraph) + "\n\n";
        tempContent.append(paraText);
        splitIfOverLength(); // 检查是否超长
    }

    /**
     * 处理段落：临时拼接，后续长度控制
     */
    private void handleCode(Code code) {
        String literal = code.getLiteral();
        tempContent.append(literal);
    }


    // ------------------------------ 辅助方法 ------------------------------

    /**
     * 处理列表项：临时拼接
     */
    private void handleListItem(ListItem listItem) {
        // 判断有序/无序列表
        Node parent = listItem.getParent();
        String prefix = (parent instanceof OrderedList) ?
                ((OrderedList) parent).getStartNumber() + ". " : "- ";

        String itemText = prefix + getNodeText(listItem) + "\n";
        tempContent.append(itemText);
        splitIfOverLength(); // 检查是否超长
    }

    /**
     * 计算文本的 Token 数
     */
    private int countTokens(String text) {
        return tokenEncoder.countTokens(text);
    }

    /**
     * 提取节点的 Markdown 文本（保留语法）
     */
    private String getNodeText(Node node) {
        StringBuilder sb = new StringBuilder();
        node.accept(new AbstractVisitor() {
            @Override
            public void visit(Text text) {
                sb.append(text.getLiteral());
                super.visit(text);
            }

            @Override
            public void visit(Link link) {
                sb.append("[").append(getNodeText(link.getFirstChild())).append("](").append(link.getDestination()).append(")");
            }

            @Override
            public void visit(Image image) {
                sb.append("![").append(image.getTitle()).append("](").append(image.getDestination()).append(")");
            }

            @Override
            public void visit(CustomBlock table) {
                if (table instanceof TableBlock) {
                    TableBlock tableBlock = (TableBlock) table;
                    // 特殊处理表格：遍历行和单元格
                    TableBlockTextExtractor tableBlockTextExtractor = new TableBlockTextExtractor();
                    String tableMarkdown = tableBlockTextExtractor.extractTableMarkdown(tableBlock);
                    sb.append(tableMarkdown);
                }

            }
        });
        return sb.toString().trim();
    }

    /**
     * 更新章节标题（按层级嵌套）
     */
    private void updateChapter(String headingText, int level) {
        if (level == 1) {
            currentChapter = headingText;
        } else {
            String[] parts = currentChapter.split(" > ");
            List<String> newParts = new ArrayList<>(Arrays.asList(parts).subList(0, level - 1));
            newParts.add(headingText);
            currentChapter = String.join(" > ", newParts);
        }
    }

    /**
     * 保存临时内容（段落/列表），并进行长度控制
     */
    private void saveTempContent() {
        if (tempContent.length() == 0) return;

        String content = tempContent.toString().trim();
        if (content.isEmpty()) {
            tempContent.setLength(0);
            return;
        }

        if (countTokens(content) <= MAX_TOKEN_PER_CHUNK) {
            addChunk(ChunkType.CONTENT, content);
            tempContent.setLength(0);
        } else {
            String s = splitBySentence(content);// 超长则按句子拆分
            tempContent.setLength(0);
            tempContent.append(s);
        }

    }

    /**
     * 检查临时内容是否超长，需要拆分
     */
    private void splitIfOverLength() {
        if (countTokens(tempContent.toString()) > MAX_TOKEN_PER_CHUNK) {
            saveTempContent();
        }
    }

    /**
     * 按句子拆分长文本（支持中英文句号）
     */
    private String splitBySentence(String content) {
        List<String> sentences = new ArrayList<>();
        Pattern pattern = Pattern.compile("([^。.]+[。.])");
        pattern.matcher(content).results().forEach(match -> sentences.add(match.group(1)));

        StringBuilder current = new StringBuilder();
        for (String sentence : sentences) {
            int newCount = countTokens(current + sentence);
            if (newCount > MAX_TOKEN_PER_CHUNK - CHUNK_OVERLAP) {
                if (current.length() > 0) {
                    addChunk(ChunkType.CONTENT, current.toString().trim());
                    // 保留重叠部分
                    current = new StringBuilder();
                }
            }
            current.append(sentence);
        }
        return current.toString().trim();

//        if (current.length() > 0) {
//            addChunk(ChunkType.CONTENT, current.toString().trim());
//        }
    }

    /**
     * 获取内容末尾的重叠部分
     */
    private String getOverlap(String content) {
        if (countTokens(content) <= CHUNK_OVERLAP) {
            return content;
        }
//        IntArrayList tokens = tokenEncoder.encode(content);
//
//        List<String> overlapTokens = tokens.subList(tokens.size() - CHUNK_OVERLAP, tokens.size());
//        return tokenEncoder.decode(overlapTokens);
        return content;
    }

    /**
     * 添加分块到结果（含元信息）
     */
    private void addChunk(ChunkType type, String content, String... extraMeta) {
        int tokenCount = countTokens(content);
        StringBuilder meta = new StringBuilder();
        meta.append("【章节：").append(currentChapter).append("】")
                .append("【类型：").append(type).append("】")
                .append("【Token数：").append(tokenCount).append("】");
        for (String extra : extraMeta) {
            meta.append("【").append(extra).append("】");
        }

        chunkResult.add(new Chunk(type, meta.toString(), content, tokenCount));
    }

    /**
     * 重置临时状态
     */
    private void resetTempState() {
        currentChapter = "根文档";
        chunkResult.clear();
        tempContent.setLength(0);
    }

    // ------------------------------ 分块模型 ------------------------------
    public enum ChunkType {
        HEADING, CODE_BLOCK, TABLE, IMAGE, CONTENT
    }

    public static class Chunk {
        private final ChunkType type;
        private final String meta;
        private final String content;
        private final int tokenCount;

        public Chunk(ChunkType type, String meta, String content, int tokenCount) {
            this.type = type;
            this.meta = meta;
            this.content = content;
            this.tokenCount = tokenCount;
        }

        // Getter 方法
        public ChunkType getType() {
            return type;
        }

        public String getMeta() {
            return meta;
        }

        public String getContent() {
            return content;
        }

        public int getTokenCount() {
            return tokenCount;
        }

        @Override
        public String toString() {
            return meta + "\n" + content + "\n" + "-".repeat(60);
        }
    }
}