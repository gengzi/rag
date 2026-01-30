package com.gengzi.rag.agent.texttosql.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 缓存清理调度器
 * 
 * <p>
 * 定期清理过期的缓存文件
 * </p>
 * 
 * <h3>功能：</h3>
 * <ul>
 * <li>每小时扫描一次缓存目录</li>
 * <li>删除超过1天未访问的文件</li>
 * <li>自动清理空目录</li>
 * <li>记录清理日志</li>
 * </ul>
 * 
 * <h3>配置：</h3>
 * <p>
 * 需要在Spring Boot主类或配置类上添加 @EnableScheduling 注解
 * </p>
 * 
 * @author gengzi
 */
@Component
public class CacheCleanupScheduler {

    private static final Logger logger = LoggerFactory.getLogger(CacheCleanupScheduler.class);

    @Autowired
    private CacheCleanupTool cacheCleanupTool;

    /**
     * 每小时清理一次过期缓存
     * 
     * <p>
     * 执行时间：每小时的整点（00:00, 01:00, 02:00, ...）
     * </p>
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void cleanupExpiredCache() {
        logger.info("开始定时清理过期缓存...");

        try {
            cacheCleanupTool.apply(new CacheCleanupTool.CleanupRequest());
            logger.info("定时清理完成");

        } catch (Exception e) {
            logger.error("定时清理过程中发生错误", e);
        }
    }

    /**
     * 每天凌晨3点执行一次深度清理
     * 
     * <p>
     * 执行时间：每天03:00:00
     * </p>
     * <p>
     * 执行更彻底的清理，包括检查文件完整性等
     * </p>
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void deepCleanup() {
        logger.info("开始深度清理缓存...");

        try {
            // 执行常规清理
            cacheCleanupTool.apply(new CacheCleanupTool.CleanupRequest());

            // 可以在这里添加其他深度清理逻辑
            // 例如：检查文件完整性、清理损坏的文件等

            logger.info("深度清理完成");

        } catch (Exception e) {
            logger.error("深度清理过程中发生错误", e);
        }
    }
}
