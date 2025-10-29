package com.gengzi.utils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class InstantConverter {

    /**
     * 将Instant转换为yyyy-MM-dd HH:mm:ss格式的字符串
     * @param instant 要转换的Instant对象
     * @param zoneId 时区（如ZoneId.systemDefault()获取系统默认时区）
     * @return 格式化后的日期时间字符串
     */
    public static String instantToString(Instant instant, ZoneId zoneId) {
        if (instant == null) {
            return null;
        }
        // 定义目标格式
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        // 将Instant转换为指定时区的ZonedDateTime，再格式化
        return formatter.format(instant.atZone(zoneId));
    }

    // 示例用法
    public static void main(String[] args) {
        Instant now = Instant.now();
        // 使用系统默认时区
        String result = instantToString(now, ZoneId.systemDefault());
        System.out.println(result); // 输出类似：2025-10-07 15:30:45
        
        // 也可以指定特定时区，如UTC
        String utcResult = instantToString(now, ZoneId.of("UTC"));
        System.out.println(utcResult); // 输出UTC时区的对应时间
    }
}
