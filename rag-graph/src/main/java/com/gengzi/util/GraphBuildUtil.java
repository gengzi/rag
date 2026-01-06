package com.gengzi.util;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;

import java.time.LocalDate;
import java.util.Date;

/**
 * 图构建相关的工具方法（基于 Hutool）。
 */
public final class GraphBuildUtil {

    private GraphBuildUtil() {
    }

    /**
     * 返回第一个非空白字符串。
     */
    public static String firstNonBlank(String... values) {
        return StrUtil.firstNonBlank(values);
    }

    /**
     * Date 转 LocalDate。
     */
    public static LocalDate toLocalDate(Date value) {
        if (value == null) {
            return null;
        }
        return DateUtil.toLocalDateTime(value).toLocalDate();
    }
}
