import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.markdown.MarkdownRenderer;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MarkdownSplitterWithoutSourcePos {
    // 特殊元素标记（用于定位）
    private static final String MARK_PREFIX = "___SPECIAL_MARK_";
    private static final String MARK_SUFFIX = "___";
    private static int markCounter = 0;

    /**
     * 核心方法：通过标记定位特殊元素
     */
    public static List<MarkdownBlock> split(String markdown) {
        List<MarkdownBlock> blocks = new ArrayList<>();
        if (markdown == null || markdown.isEmpty()) return blocks;
        Map<String, String> markTypeMap = new HashMap<>(); // 标记→元素类型
        // 步骤1：给原文中的特殊元素添加唯一标记
        String markedMarkdown = addSpecialMarks(markdown, markTypeMap);

        // 步骤2：解析带标记的文本，提取标记位置和对应元素类型

        parseMarkedMarkdown(markedMarkdown, markTypeMap);

        // 步骤3：通过标记分割原文
        return splitByMarks(markdown, markedMarkdown, markTypeMap);
    }

    /**
     * 给原文中的特殊元素添加唯一标记（不修改原文内容，仅在前后加标记）
     */
    private static String addSpecialMarks(String original, Map<String, String> markTypeMap) {
        Parser parser = Parser.builder()
                .extensions(Arrays.asList(
                        TablesExtension.create()
                ))
                .build();
        Node document = parser.parse(original);
        MarkdownRenderer renderer = MarkdownRenderer.builder()
                .extensions(Arrays.asList(TablesExtension.create()))
                .build();

        // 给特殊元素添加标记
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

            @Override
            public void visit(IndentedCodeBlock code) {
                wrapNodeWithMark(code, "code");
                super.visit(code);
            }

            // 给节点前后添加标记
            private void wrapNodeWithMark(Node node, String type) {
                String mark = generateUniqueMark();
                markTypeMap.put(mark, type); // 记录标记对应的类型

                // 在节点前添加标记
                Text prefix = new Text(mark + "\n\n");

                node.insertBefore(prefix);

                // 在节点后添加标记
                Text suffix = new Text("\n\n" + mark);
                node.insertAfter(suffix);
            }
        });

        return renderer.render(document);
    }

    // 生成唯一标记
    private static String generateUniqueMark() {
        return MARK_PREFIX + (markCounter++) + MARK_SUFFIX;
    }

    // 解析带标记的文本，确认标记对应关系（冗余检查）
    private static void parseMarkedMarkdown(String marked, Map<String, String> markTypeMap) {
        Parser parser = Parser.builder()
                .extensions(Arrays.asList(
                        TablesExtension.create()
                ))
                .build();
        Node document = parser.parse(marked);
        document.accept(new AbstractVisitor() {
            @Override
            public void visit(Text text) {
                String literal = text.getLiteral();
                if (literal.contains(MARK_PREFIX)) {
                    // 提取标记并确认类型映射
                    Pattern pattern = Pattern.compile(MARK_PREFIX + "\\d+" + MARK_SUFFIX);
                    Matcher matcher = pattern.matcher(literal);
                    while (matcher.find()) {
                        String mark = matcher.group();
                        if (!markTypeMap.containsKey(mark)) {
                            markTypeMap.remove(mark); // 清理无效标记
                        }
                    }
                }
                super.visit(text);
            }
        });
    }

    /**
     * 根据标记分割原文
     */
    private static List<MarkdownBlock> splitByMarks(
            String original,
            String marked,
            Map<String, String> markTypeMap) {

        List<MarkdownBlock> blocks = new ArrayList<>();
        int positionCounter = 0;
        int lastEnd = 0;

        // 提取所有标记并按出现顺序排序
        List<String> sortedMarks = new ArrayList<>(markTypeMap.keySet());
        sortedMarks.sort(Comparator.comparingInt(m -> marked.indexOf(m)));

        // 按标记位置分割
        for (String mark : sortedMarks) {
            int markStart = marked.indexOf(mark, lastEnd);
            if (markStart == -1) break;

            // 1. 标记前的上下文内容（从上次结束到当前标记前）
            int contextEnd = markStart;
            if (contextEnd > lastEnd) {
                // 从原始文本中截取对应位置的内容
                String context = extractOriginalContent(original, marked, lastEnd, contextEnd, markTypeMap);
                if (!context.trim().isEmpty()) {
                    blocks.add(new MarkdownBlock("context", context.trim(), positionCounter++));
                }
            }

            // 2. 找到对应的结束标记
            int markEnd = marked.indexOf(mark, markStart + 1);
            if (markEnd == -1) {
                lastEnd = markStart + mark.length();
                continue;
            }

            // 3. 提取特殊元素内容（两个标记之间）
            String specialContent = extractOriginalContent(original, marked, markStart + mark.length(), markEnd, markTypeMap);
            blocks.add(new MarkdownBlock(
                    markTypeMap.get(mark),
                    specialContent.trim(),
                    positionCounter++
            ));

            lastEnd = markEnd + mark.length();
        }

        // 4. 处理剩余内容
        if (lastEnd < marked.length()) {
            String lastContext = extractOriginalContent(original, marked, lastEnd, marked.length(), markTypeMap);
            if (!lastContext.trim().isEmpty()) {
                blocks.add(new MarkdownBlock("context", lastContext.trim(), positionCounter++));
            }
        }

        return blocks;
    }

    /**
     * 从原始文本中提取对应位置的内容（核心：忽略标记的影响）
     */
    private static String extractOriginalContent(
            String original,
            String marked,
            int markedStart,
            int markedEnd, Map<String, String> markTypeMap) {

        // 计算标记导致的偏移量（marked比original多出来的字符数）
        int offset = 0;
        String markedSub = marked.substring(0, markedStart);
        for (String mark : markTypeMap.keySet()) {
            int count = countOccurrences(markedSub, mark);
            offset += count * mark.length() * 2; // 每个标记在前后各出现一次
        }

        // 计算原始文本中的对应位置
        int originalStart = markedStart - offset;
        int originalEnd = markedEnd - offset;

        // 边界检查
        originalStart = Math.max(0, originalStart);
        originalEnd = Math.min(original.length(), originalEnd);

        return original.substring(originalStart, originalEnd);
    }

    // 统计字符串出现次数
    private static int countOccurrences(String text, String target) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(target, index)) != -1) {
            count++;
            index += target.length();
        }
        return count;
    }

    // 测试方法
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
        List<MarkdownBlock> blocks = split(testMd);
        for (MarkdownBlock block : blocks) {
            System.out.println("Type: " + block.type);
            System.out.println("Content: " + block.content);
            System.out.println("Position: " + block.position);
            System.out.println("-------------------------");
        }

    }

    // 块信息封装
    static class MarkdownBlock {
        String type;       // context/image/table/code
        String content;    // 原文内容
        int position;      // 顺序位置

        public MarkdownBlock(String type, String content, int position) {
            this.type = type;
            this.content = content;
            this.position = position;
        }
    }
}
