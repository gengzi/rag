package com.gengzi.request;

import lombok.Data;

@Data
public class ExcelQueryRequest {
    /**
     * SQL 查询语句，例如：
     * SELECT * FROM read_xlsx('D:/data.xlsx', sheet='Sheet1') WHERE 姓名 = '张三' LIMIT 10
     * SELECT * FROM read_csv_auto('D:/data.csv') WHERE 年龄 > 25
     */
    private String sql;

    /**
     * 文件路径（可选，用于SQL中的表名替换）
     */
    private String filePath;

    /**
     * Sheet 名称（仅用于 Excel）
     */
    private String sheetName;

    /**
     * 是否为 CSV 文件
     */
    private Boolean isCsv;
}
