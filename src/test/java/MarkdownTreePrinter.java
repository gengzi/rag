import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;

public class MarkdownTreePrinter {

    // 打印 Markdown 文档的 AST 树结构
    public static void printTree(String markdown) {
        Parser parser = Parser.builder().build();
        Node document = parser.parse(markdown);

        System.out.println("Markdown 抽象语法树结构：");
        System.out.println("==========================");
        traverseTree(document, 0); // 从根节点开始遍历，初始层级为 0
    }

    // 递归遍历节点树并打印
    private static void traverseTree(Node node, int depth) {
        // 打印当前节点的层级（用空格表示缩进）
        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            indent.append("  "); // 每层缩进 2 个空格
        }

        // 打印节点类型和核心属性
        String nodeInfo = getNodeInfo(node);
        System.out.println(indent + nodeInfo);

        // 递归遍历子节点（层级+1）
        Node child = node.getFirstChild();
        while (child != null) {
            traverseTree(child, depth + 1);
            child = child.getNext();
        }
    }

    // 获取节点的关键信息（类型+核心属性）
    private static String getNodeInfo(Node node) {
        if (node instanceof Document) {
            return "Document (根节点)";
        } else if (node instanceof Heading) {
            Heading heading = (Heading) node;
            return "Heading (级别: " + heading.getLevel() + ")";
        } else if (node instanceof Paragraph) {
            return "Paragraph (段落)";
        } else if (node instanceof Text) {
            Text text = (Text) node;
            // 文本内容过长时截断显示
            String content = text.getLiteral();
            if (content.length() > 30) {
                content = content.substring(0, 30) + "...";
            }
            return "Text (内容: \"" + content + "\")";
        } else if (node instanceof Image) {
            Image image = (Image) node;
            return "Image (描述: \"" + image.getTitle() + "\", 地址: " + image.getDestination() + ")";
        } else if (node instanceof Link) {
            Link link = (Link) node;
            return "Link (文本: \"" + getLinkText(link) + "\", 地址: " + link.getDestination() + ")";
        } else if (node instanceof FencedCodeBlock) {
            FencedCodeBlock code = (FencedCodeBlock) node;
            return "FencedCodeBlock (语言: " + (code.getInfo() == null ? "无" : code.getInfo()) + ")";
        } else if (node instanceof IndentedCodeBlock) {
            return "IndentedCodeBlock (缩进代码块)";
        } else if (node instanceof TableBlock) {
            return "Table (表格)";
        } else if (node instanceof TableRow) {
            return "TableRow (表格行)";
        } else if (node instanceof TableCell) {
            return "TableCell (表格单元格)";
        } else if (node instanceof BulletList) {
            return "BulletList (无序列表)";
        } else if (node instanceof OrderedList) {
            OrderedList list = (OrderedList) node;
            return "OrderedList (有序列表, 起始编号: " + list.getStartNumber() + ")";
        } else if (node instanceof ListItem) {
            return "ListItem (列表项)";
        } else if (node instanceof BlockQuote) {
            return "BlockQuote (引用块)";
        } else if (node instanceof Code) {
            Code code = (Code) node;
            return "Code (行内代码: \"" + code.getLiteral() + "\")";
        } else if (node instanceof Emphasis) {
            return "Emphasis (斜体)";
        } else if (node instanceof StrongEmphasis) {
            return "StrongEmphasis (加粗)";
        }

        // 未覆盖的节点类型
        return node.getClass().getSimpleName() + " (未知节点)";
    }

    // 辅助方法：获取链接的文本内容
    private static String getLinkText(Link link) {
        Node child = link.getFirstChild();
        if (child instanceof Text) {
            return ((Text) child).getLiteral();
        }
        return "无文本";
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
        printTree(testMd);
    }
}