import org.commonmark.ext.gfm.tables.*;
import org.commonmark.node.Node;
import org.commonmark.node.AbstractVisitor;
import java.util.ArrayList;
import java.util.List;

/**
 * 从 TableBlock 提取表格原文（适配 commonmark-ext-gfm-tables 0.22.0）
 */
public class TableBlockTextExtractor {

    /**
     * 提取表格的完整 Markdown 原文
     */
    public static String extractTableMarkdown(TableBlock tableBlock) {
        StringBuilder markdown = new StringBuilder();
        List<List<String>> rows = new ArrayList<>();

        // 1. 收集所有行（表头 + 数据）
        collectTableRows(tableBlock, rows);

        // 2. 拼接表头行
        if (!rows.isEmpty()) {
            markdown.append(buildRow(rows.get(0))).append("\n");
            // 3. 拼接分隔线
            markdown.append(buildSeparator(rows.get(0).size())).append("\n");
            // 4. 拼接数据行
            for (int i = 1; i < rows.size(); i++) {
                markdown.append(buildRow(rows.get(i))).append("\n");
            }
        }

        return markdown.toString().trim();
    }

    /**
     * 收集表格的所有行（表头 + 数据）
     */
    private static void collectTableRows(TableBlock tableBlock, List<List<String>> rows) {
        Node child = tableBlock.getFirstChild();
        while (child != null) {
            if (child instanceof TableHead || child instanceof TableBody) {
                Node rowNode = child.getFirstChild();
                while (rowNode != null) {
                    if (rowNode instanceof TableRow) {
                        List<String> cells = new ArrayList<>();
                        Node cellNode = rowNode.getFirstChild();
                        while (cellNode != null) {
                            if (cellNode instanceof TableCell) {
                                cells.add(extractCellText((TableCell) cellNode));
                            }
                            cellNode = cellNode.getNext();
                        }
                        rows.add(cells);
                    }
                    rowNode = rowNode.getNext();
                }
            }
            child = child.getNext();
        }
    }

    /**
     * 提取单元格内的文本（保留链接、格式等原始语法）
     */
    private static String extractCellText(TableCell cell) {
        StringBuilder text = new StringBuilder();
        cell.accept(new AbstractVisitor() {
            @Override
            public void visit(org.commonmark.node.Text textNode) {
                text.append(textNode.getLiteral());
            }

            @Override
            public void visit(org.commonmark.node.Link link) {
                text.append("[")
                    .append(extractCellText((TableCell) link.getFirstChild()))
                    .append("](")
                    .append(link.getDestination())
                    .append(")");
            }

            @Override
            public void visit(org.commonmark.node.StrongEmphasis strong) {
                text.append("**")
                    .append(extractCellText((TableCell) strong.getFirstChild()))
                    .append("**");
            }
        });
        return text.toString().trim();
    }

    /**
     * 构建单行 Markdown（如 "| 表头1 | 表头2 |"）
     */
    private static String buildRow(List<String> cells) {
        StringBuilder row = new StringBuilder("| ");
        for (String cell : cells) {
            row.append(cell).append(" | ");
        }
        return row.toString().replaceAll(" \\| $", " |"); // 去除末尾多余空格
    }

    /**
     * 构建分隔线（如 "| --- | --- |"）
     */
    private static String buildSeparator(int columnCount) {
        StringBuilder sep = new StringBuilder("|");
        for (int i = 0; i < columnCount; i++) {
            sep.append(" --- |");
        }
        return sep.toString();
    }
}