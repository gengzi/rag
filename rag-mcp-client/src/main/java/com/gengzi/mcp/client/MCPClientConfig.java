package com.gengzi.mcp.client;


import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;


/**
 * 在真正使用某个mcp 服务的时候，请将 mcpClient 注入到对应的类中，并调用 mcpClient.init() 方法进行初始化
 */
@Configuration
public class MCPClientConfig implements InitializingBean {
    private static final Logger logger = LoggerFactory.getLogger(MCPClientConfig.class);
    @Autowired
    private List<McpSyncClient> mcpClients;


    /**
     * 动态返回可用的McpSyncClient列表，对于访问服务不可达的，抛弃出去
     */
    public List<McpSyncClient> mcpSyncClients() {
        List<McpSyncClient> surviveMcpSyncClients = new ArrayList<>();

        for (McpSyncClient mcpClient : mcpClients) {
            // 进行ping 操作，看链接是否正常
            boolean flag = ping(mcpClient);
            if (!flag) {
                continue;
            }
            if (!mcpClient.isInitialized() && flag) {
                // 未初始化的进行初始化
                try {
                    McpSchema.InitializeResult initialize = mcpClient.initialize();
                    logger.info("系统加载-mcpClient 初始化：{}", initialize);
                } catch (Exception e) {
                    logger.error("mcpClient:{} 初始化失败：{}", mcpClient.getClientInfo().name(), e);
                    continue;
                }
            }
            surviveMcpSyncClients.add(mcpClient);
        }
        logger.debug("可用的McpSyncClient列表：{}", surviveMcpSyncClients);
        return surviveMcpSyncClients;
    }


    private boolean ping(McpSyncClient mcpClient) {
        // 已经初始化的，进行ping 操作，看链接是否正常
        try {
            Object ping = mcpClient.ping();
            return true;
        } catch (Exception e) {
            // 不抛出异常认为存活
            logger.error("mcpClient:{} ping 失败：{}", mcpClient.getClientInfo().name(), e);
        }
        return false;
    }


    @Override
    public void afterPropertiesSet() throws Exception {
        for (McpSyncClient mcpClient : mcpClients) {
            if (!mcpClient.isInitialized()) {
                try {
                    McpSchema.InitializeResult initialize = mcpClient.initialize();
                    logger.info("系统加载-mcpClient 初始化：{}", initialize);
                } catch (Exception e) {
                    logger.error("mcpClient:{} 初始化失败!!!!!：{}", mcpClient.getClientInfo().name(), e);
                    e.printStackTrace();
                }
            }
        }
    }
}
