package com.gengzi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 知识图谱构建配置属性类
 *
 * <p>负责管理知识图谱构建的相关配置，包括：</p>
 * <ul>
 *   <li>Elasticsearch 索引名称</li>
 *   <li>批量查询大小</li>
 *   <li>启动时自动构建开关</li>
 * </ul>
 *
 * <p>配置前缀：`rag.graph`</p>
 *
 * <p>配置示例（application.yml）：</p>
 * <pre>
 * rag:
 *   graph:
 *     index-name: rag_store_new
 *     batch-size: 200
 *     on-startup: false
 * </pre>
 *
 * @author RAG Graph Development Team
 * @version 1.0
 * @see ConfigurationProperties
 */
@ConfigurationProperties(prefix = "rag.graph")
public class RagGraphBuildProperties {

    /**
     * Elasticsearch 索引名称
     *
     * <p>指定要从中读取文档数据的 Elasticsearch 索引。
     * 该索引应包含文档及其分块的完整数据。</p>
     *
     * <p>默认值：`rag_store_new`</p>
     * <p>可在配置文件中通过 `rag.graph.index-name` 覆盖</p>
     *
     * <p>索引结构要求：</p>
     * <ul>
     *   <li>必须包含 docId 字段（文档 ID）</li>
     *   <li>必须包含文档分块内容字段</li>
     *   <li>建议包含文档元数据（标题、创建时间等）</li>
     * </ul>
     */
    private String indexName = "rag_store_new";

    /**
     * 批量查询大小
     *
     * <p>从 Elasticsearch 批量查询文档时每批的文档数量。
     * 较大的值可以减少查询次数，但会增加内存使用。</p>
     *
     * <p>默认值：`200`</p>
     * <p>可在配置文件中通过 `rag.graph.batch-size` 覆盖</p>
     *
     * <p>调优建议：</p>
     * <ul>
     *   <li>文档较小时（< 10KB）：可设置为 500-1000</li>
     *   <li>文档中等时（10-100KB）：建议设置为 100-500</li>
     *   <li>文档较大时（> 100KB）：建议设置为 50-100</li>
     *   <li>内存受限时：适当减小此值</li>
     * </ul>
     */
    private int batchSize = 200;

    /**
     * 启动时自动构建开关
     *
     * <p>控制是否在应用启动时自动触发知识图谱构建。
     * 如果设置为 true，应用启动后会自动从 ES 读取数据并构建图谱。</p>
     *
     * <p>默认值：`false`</p>
     * <p>可在配置文件中通过 `rag.graph.on-startup` 覆盖</p>
     *
     * <p>使用场景：</p>
     * <ul>
     *   <li>true：适合首次部署或需要确保图谱始终最新</li>
     *   <li>false：推荐值，避免启动时长时间阻塞，改用手动触发</li>
     * </ul>
     *
     * <p>注意事项：</p>
     * <ul>
     *   <li>启动时构建会延长应用启动时间</li>
     *   <li>如果索引数据量大，启动时间可能显著增加</li>
     *   <li>建议生产环境设置为 false，使用外部触发</li>
     * </ul>
     */
    private boolean onStartup = false;

    /**
     * 获取 Elasticsearch 索引名称
     *
     * @return 索引名称
     */
    public String getIndexName() {
        return indexName;
    }

    /**
     * 设置 Elasticsearch 索引名称
     *
     * @param indexName 索引名称
     */
    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    /**
     * 获取批量查询大小
     *
     * @return 每批查询的文档数量
     */
    public int getBatchSize() {
        return batchSize;
    }

    /**
     * 设置批量查询大小
     *
     * @param batchSize 每批查询的文档数量
     */
    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    /**
     * 是否在启动时自动构建图谱
     *
     * @return true 表示启动时自动构建，false 表示不自动构建
     */
    public boolean isOnStartup() {
        return onStartup;
    }

    /**
     * 设置启动时自动构建开关
     *
     * @param onStartup true 表示启动时自动构建，false 表示不自动构建
     */
    public void setOnStartup(boolean onStartup) {
        this.onStartup = onStartup;
    }
}