```sql
PUT /rag_stroe_mcp
{
  "settings": {
    "number_of_shards": 3,  // 主分片数（根据数据量调整）
    "number_of_replicas": 1, // 副本数（保证高可用）
 "index.knn": true,  // 开启 k-NN 功能（关键配置）
    "analysis": {
      "analyzer": {
        "ik_smart_pinyin": {  // 中文+拼音混合分词器（提升搜索体验）
          "type": "custom",
          "tokenizer": "ik_smart",
          "filter": ["pinyin_filter"]
        },
        "ik_max_word_pinyin": {  // 细粒度中文+拼音分词器
          "type": "custom",
          "tokenizer": "ik_max_word",
          "filter": ["pinyin_filter"]
        }
      },
      "filter": {
        "pinyin_filter": {  // 拼音过滤器（支持拼音搜索）
          "type": "pinyin",
          "keep_full_pinyin": true,
          "keep_joined_full_pinyin": true,
          "keep_original": true
        }
      }
    }
  },
  "mappings": {
    "properties": {
      // 1. 对应 user_memory 表核心字段
      "user_id": { 
        "type": "keyword"  // 精确匹配（多租户隔离），支持聚合和筛选
      },
      "type": { 
        "type": "keyword"  // 与表中 enum 对应，支持精确匹配（如筛选 preference 类型）
      },
      "content": { 
        "type": "text",    // 全文检索字段
        "analyzer": "ik_max_word_pinyin",  // 细粒度分词（含拼音）
        "search_analyzer": "ik_smart_pinyin",  // 搜索时用粗粒度分词提升效率
        "fields": {
          "keyword": { 
            "type": "keyword"  // 用于精确匹配或聚合（如分组统计相同内容）
          }
        }
      },
      "created_time": { 
        "type": "date",    // 时间类型，支持范围查询（如按创建时间筛选）
        "format": "yyyy-MM-dd HH:mm:ss.SSS"  // 与表中 datetime(3) 格式匹配
      },
      "updated_time": { 
        "type": "date",
        "format": "yyyy-MM-dd HH:mm:ss.SSS"
      },
      "confidence": { 
        "type": "float"    // 置信度，支持范围查询（如筛选 confidence > 0.8）
      },
      // 2. Spring AI Document 相关字段（向量检索支持）
      "q_1024_vec": {  // 文本向量字段（如通过 Spring AI Embedding 模型生成）
        "type": "knn_vector",  // 支持 k-NN 向量检索
        "dimension": 1024,      // 向量维度（根据模型调整，如 BERT 类模型常用 768）
        "method": {
          "name": "hnsw",      // 高效近似检索算法
          "engine": "lucene",
          "space_type": "cosinesimil"  // 语义相似度计算（余弦相似度）
        }
      },
      "metadata": {  // Spring AI Document 元数据（灵活存储额外信息）
        "type": "object",     // 嵌套对象类型
        "dynamic": true       // 支持动态字段（元数据结构不固定时启用）
      }
    }
  }
}

                                                   
                                                   
                                                   
                                                   

PUT /rag_stroe_mcp
{
  "settings": {
    "number_of_shards": 3,
    "number_of_replicas": 1,
    "index.knn": true,
    "analysis": {
      "analyzer": {
        "ik_smart_pinyin": {
          "type": "custom",
          "tokenizer": "ik_smart",
          "filter": ["pinyin_filter"]
        },
        "ik_max_word_pinyin": {
          "type": "custom",
          "tokenizer": "ik_max_word",
          "filter": ["pinyin_filter"]
        }
      },
      "filter": {
        "pinyin_filter": {
          "type": "pinyin",
          "keep_full_pinyin": true,
          "keep_joined_full_pinyin": true,
          "keep_original": true
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "user_id": {
        "type": "keyword"
      },
      "type": {
        "type": "keyword"
      },
      "content": {
        "type": "text",
        "analyzer": "ik_max_word_pinyin",
        "search_analyzer": "ik_smart_pinyin",
        "fields": {
          "keyword": {
            "type": "keyword"
          }
        }
      },
      "created_time": {
        "type": "date",
        "format": "yyyy-MM-dd HH:mm:ss.SSS"
      },
      "updated_time": {
        "type": "date",
        "format": "yyyy-MM-dd HH:mm:ss.SSS"
      },
      "confidence": {
        "type": "float"
      },
      "q_1024_vec": {
        "type": "knn_vector",
        "dimension": 1024,
        "method": {
          "name": "hnsw",
          "engine": "lucene",
          "space_type": "cosinesimil"
        }
      },
      "metadata": {
        "type": "object",
        "dynamic": true
      }
    }
  }
}





```


## 安装分词器
https://github.com/infinilabs/analysis-ik
测试分词器
```sql
POST /_analyze
{
  "text": ["亚马逊云科技"],
  "analyzer": "ik_max_word"
}

POST /_analyze
{
  "text": ["亚马逊云科技"],
  "analyzer": "pinyin"
}
     
```

### 创建搜索管道
用来处理权重占比
```sql

PUT /_search/pipeline/rag_store_mcp_search_pipeline
{
  "description": "Post processor for hybrid search",
  "phase_results_processors": [
    {
      "normalization-processor": {
        "normalization": {
          "technique": "min_max"
        },
        "combination": {
          "technique": "arithmetic_mean",
          "parameters": {
            "weights": [
              0.4,
              0.6
            ]
          }
        }
      }
    }
  ]
}


```