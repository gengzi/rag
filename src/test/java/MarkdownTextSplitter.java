import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 仿 LangChain MarkdownTextSplitter 的 Java 实现
 * 核心逻辑：按 Markdown 结构优先级拆分，结合长度控制和上下文重叠
 */
public class MarkdownTextSplitter {

    // 默认拆分优先级：从高到低（与 LangChain 保持一致）
    private static final List<String> DEFAULT_SEPARATORS = Arrays.asList(
            "\n# ", "\n## ", "\n### ", "\n#### ", "\n##### ", "\n###### ",  // 标题
            "\n```\n",                                                     // 代码块
            "\n---\n", "\n***\n", "\n___\n",                               // 水平线
            "\n- ", "\n1. ",                                               // 列表
            "\n\n",                                                        // 空行
            ". ", " "                                                      // 句子/空格（兜底）
    );

    private final int chunkSize;               // 每个 chunk 的最大长度（字符/Token）
    private final int chunkOverlap;            // 相邻 chunk 的重叠长度
    private final List<String> separators;     // 拆分优先级列表
    private final Function<String, Integer> lengthFunction;  // 长度计算函数（字符/Token）

    // 构造器：使用默认配置（字符计数）
    public MarkdownTextSplitter(int chunkSize, int chunkOverlap) {
        this(chunkSize, chunkOverlap, DEFAULT_SEPARATORS, String::length);
    }

    // 构造器：自定义配置（支持 Token 计数）
    public MarkdownTextSplitter(
            int chunkSize,
            int chunkOverlap,
            List<String> separators,
            Function<String, Integer> lengthFunction
    ) {
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        this.separators = new ArrayList<>(separators);
        this.lengthFunction = lengthFunction;
    }

    /**
     * 拆分 Markdown 文本为 chunks
     */
    public List<String> split(String markdownContent) {
        // 1. 预处理：统一换行符，避免格式问题
        String processedContent = markdownContent.replace("\r\n", "\n").replace("\r", "\n");

        // 2. 按优先级递归拆分
        List<String> initialChunks = splitWithSeparators(processedContent, separators);

        // 3. 处理重叠和长度兜底（确保最终 chunk 符合大小要求）
        List<String> finalChunks = new ArrayList<>();
        for (String chunk : initialChunks) {
            // 若 chunk 过长，进一步按更低优先级分隔符拆分
            if (lengthFunction.apply(chunk) > chunkSize) {
                // 找到当前 chunk 中存在的最高优先级分隔符
                String bestSeparator = separators.stream()
                        .filter(sep -> chunk.contains(sep))
                        .findFirst()
                        .orElse(separators.get(separators.size() - 1)); // 兜底用最后一个分隔符

                // 递归拆分
                List<String> subChunks = splitWithSeparators(chunk, Collections.singletonList(bestSeparator));
                finalChunks.addAll(handleOverlap(subChunks));
            } else {
                finalChunks.add(chunk);
            }
        }

        return finalChunks;
    }

    /**
     * 按指定分隔符列表拆分文本（递归逻辑）
     */
    private List<String> splitWithSeparators(String text, List<String> separators) {
        if (separators.isEmpty()) {
            return Collections.singletonList(text);
        }

        String separator = separators.get(0);
        List<String> parts = splitTextWithSeparator(text, separator);

        if (parts.size() == 1) {
            // 若当前分隔符无法拆分，使用下一个优先级更低的分隔符
            return splitWithSeparators(text, separators.subList(1, separators.size()));
        }

        // 递归处理子部分
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            result.addAll(splitWithSeparators(part, separators.subList(1, separators.size())));
        }

        return result;
    }

    /**
     * 用单个分隔符拆分文本（保留分隔符在拆分后的片段中）
     * 例："## a\n## b" 用 "\n## " 拆分后为 ["## a", "## b"]
     */
    private List<String> splitTextWithSeparator(String text, String separator) {
        if (separator.isEmpty()) {
            return Collections.singletonList(text);
        }

        List<String> parts = new ArrayList<>();
        int start = 0;
        int index;

        // 处理分隔符包含换行的情况（如 "\n# "）
        while ((index = text.indexOf(separator, start)) != -1) {
            // 截取从 start 到 index 的部分（不含分隔符）
            if (index > start) {
                parts.add(text.substring(start, index));
            }
            // 分隔符本身作为下一段的前缀（保留结构）
            start = index;
            // 移动到分隔符之后
            start += separator.length();
        }

        // 添加最后一段
        if (start <= text.length()) {
            parts.add(text.substring(start));
        }

        // 过滤空字符串
        return parts.stream()
                .filter(part -> !part.trim().isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * 处理相邻 chunk 的重叠（后一个 chunk 重复前一个的结尾部分）
     */
    private List<String> handleOverlap(List<String> chunks) {
        if (chunks.size() <= 1 || chunkOverlap <= 0) {
            return chunks;
        }

        List<String> overlapped = new ArrayList<>();
        overlapped.add(chunks.get(0));

        for (int i = 1; i < chunks.size(); i++) {
            String prevChunk = chunks.get(i - 1);
            String currentChunk = chunks.get(i);

            // 从上个 chunk 结尾截取 overlap 长度的内容
            String overlap = getOverlap(prevChunk, chunkOverlap);

            // 拼接重叠部分和当前 chunk
            overlapped.add(overlap + currentChunk);
        }

        return overlapped;
    }

    /**
     * 从文本末尾截取指定长度的重叠内容（避免截断单词）
     */
    private String getOverlap(String text, int overlapLength) {
        if (text.length() <= overlapLength) {
            return text; // 文本长度不足，返回全部
        }
        // 从末尾截取 overlapLength 长度，尽量在空格处截断
        int start = text.length() - overlapLength;
        // 查找最近的空格，避免截断单词
        Matcher matcher = Pattern.compile(" ").matcher(text.substring(start));
        if (matcher.find()) {
            start += matcher.start();
        }
        return text.substring(start);
    }


    // ------------------------------ 示例用法 ------------------------------
    public static void main(String[] args) {
        // 示例 Markdown 内容
        String markdown = """
                # 产品说明书
                
                ## 1. 功能介绍
                - 支持文本转换
                - 支持格式保存
                
                ## 2. 使用示例
                ```java
                public class Demo {
                    public static void main(String[] args) {
                        System.out.println("Hello");
                    }
                }
                }""";
        MarkdownTextSplitter splitter = new MarkdownTextSplitter(1000, 100);
        List<String> chunks = splitter.split(markdown);
        for (String chunk : chunks) {
            System.out.println(chunk);
        }
    }
    }