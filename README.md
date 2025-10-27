## Rag by Spring ai

### Rag流程

### 步骤


 


### 问题
宿主机运行 ollama 运行容器，以docker 运行的ragflow 配置请求地址时，需要配置 http://host.docker.internal:11434/ 才能访问到






* pdf ocr 识别
```angular2html
## 使用paddlex  将paddleocr 服务化，提供服务化接口提供使用 （https://www.paddleocr.ai/latest/version3.x/deployment/serving.html）
paddlex --serve --pipeline  PP-StructureV3   --port 8885

PP-StructureV3  https://www.paddleocr.ai/latest/version3.x/pipeline_usage/PP-StructureV3.html 使用指南
paddlex --serve --pipeline  /home/gengzi/PP-StructureV3.yaml   --port 8885

## 启动命令
进入linux服务
conda env list
conda activate env-vllm

## 启动minio
E:\ruanjian\minio.exe server  F:\sso   --address ":8886"  --console-address ":9006"
access-key: rag_flow
secret-key: infini_rag_flow

## 启动es docker

## 启动ollma服务

```


## es 映射配置
```
{
    "mappings": {
        "properties": {
            "content": {
                "type": "text",
                "analyzer": "standard"
            },
            "content_ltks": {
                "type": "text",
                "analyzer": "whitespace"
            },
            "content_sm_ltks": {
                "type": "text",
                "analyzer": "whitespace"
            },
            "create_time": {
                "type": "date",
                "format": "yyyy-MM-dd HH:mm:ss"
            },
            "create_timestamp_flt": {
                "type": "float"
            },
            "doc_id": {
                "type": "keyword"
            },
            "doc_type_kwd": {
                "type": "keyword"
            },
            "docnm_kwd": {
                "type": "keyword"
            },
            "id": {
                "type": "text",
                "fields": {
                    "keyword": {
                        "type": "keyword",
                        "ignore_above": 256
                    }
                }
            },
            "img_id": {
                "type": "keyword"
            },
            "kb_id": {
                "type": "keyword"
            },
            "metadata": {
                "properties": {
                    "contentType": {
                        "type": "text",
                        "fields": {
                            "keyword": {
                                "type": "keyword",
                                "ignore_above": 256
                            }
                        }
                    },
                    "convertedTimestamp": {
                        "type": "long"
                    },
                    "createdAt": {
                        "type": "date"
                    },
                    "documentId": {
                        "type": "text",
                        "fields": {
                            "keyword": {
                                "type": "keyword",
                                "ignore_above": 256
                            }
                        }
                    },
                    "documentType": {
                        "type": "text",
                        "fields": {
                            "keyword": {
                                "type": "keyword",
                                "ignore_above": 256
                            }
                        }
                    },
                    "excerpt_keywords": {
                        "type": "text",
                        "fields": {
                            "keyword": {
                                "type": "keyword",
                                "ignore_above": 256
                            }
                        }
                    },
                    "hasInputImage": {
                        "type": "boolean"
                    },
                    "inputImageBase64": {
                        "type": "text",
                        "fields": {
                            "keyword": {
                                "type": "keyword",
                                "ignore_above": 256
                            }
                        }
                    },
                    "isParagraphEnd": {
                        "type": "boolean"
                    },
                    "isParagraphStart": {
                        "type": "boolean"
                    },
                    "isValid": {
                        "type": "boolean"
                    },
                    "next_section_summary": {
                        "type": "text",
                        "fields": {
                            "keyword": {
                                "type": "keyword",
                                "ignore_above": 256
                            }
                        }
                    },
                    "outputImageCount": {
                        "type": "long"
                    },
                    "outputImageNames": {
                        "type": "text",
                        "fields": {
                            "keyword": {
                                "type": "keyword",
                                "ignore_above": 256
                            }
                        }
                    },
                    "pageNumber": {
                        "type": "long"
                    },
                    "pageRange": {
                        "type": "text",
                        "fields": {
                            "keyword": {
                                "type": "keyword",
                                "ignore_above": 256
                            }
                        }
                    },
                    "prev_section_summary": {
                        "type": "text",
                        "fields": {
                            "keyword": {
                                "type": "keyword",
                                "ignore_above": 256
                            }
                        }
                    },
                    "requestLogId": {
                        "type": "text",
                        "fields": {
                            "keyword": {
                                "type": "keyword",
                                "ignore_above": 256
                            }
                        }
                    },
                    "section_summary": {
                        "type": "text",
                        "fields": {
                            "keyword": {
                                "type": "keyword",
                                "ignore_above": 256
                            }
                        }
                    },
                    "sourceFileUrl": {
                        "type": "text",
                        "fields": {
                            "keyword": {
                                "type": "keyword",
                                "ignore_above": 256
                            }
                        }
                    }
                }
            },
            "pageNumInt": {
                "type": "text",
                "fields": {
                    "keyword": {
                        "type": "keyword",
                        "ignore_above": 256
                    }
                }
            },
            "page_num_int": {
                "type": "keyword",
                "index": false
            },
            "position_int": {
                "type": "keyword",
                "index": false
            },
            "q_1024_vec": {
                "type": "dense_vector",
                "dims": 1024,
                "index": true,
                "similarity": "cosine",
                "index_options": {
                    "type": "bbq_hnsw",
                    "m": 16,
                    "ef_construction": 100,
                    "rescore_vector": {
                        "oversample": 3
                    }
                }
            },
            "title_sm_tks": {
                "type": "text",
                "analyzer": "whitespace"
            },
            "title_tks": {
                "type": "text",
                "analyzer": "whitespace"
            },
            "top_int": {
                "type": "keyword",
                "index": false
            }
        }
    }
}
```
--

```angular2html

```


针对pdf 和 图片内容的解析，采用视觉模型来处理
embedding  和 对话模型 均采用开源模型
针对存储向量和检索 采用es 或者其他（）
针对全文检索，采用es
检索最终变成混合检索
对话模型仅支持文本模型
存储文档和一些解析后的文件 采用对象存储minio


对于高级功能，在实践中增加和完善
重排序模型
