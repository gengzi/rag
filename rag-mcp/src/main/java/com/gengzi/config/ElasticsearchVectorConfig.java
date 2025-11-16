//package com.gengzi.config;
//
//
//import co.elastic.clients.elasticsearch.ElasticsearchClient;
//import co.elastic.clients.json.jackson.JacksonJsonpMapper;
//import co.elastic.clients.transport.ElasticsearchTransport;
//import co.elastic.clients.transport.rest_client.RestClientTransport;
//import com.gengzi.memory.vector.es.ExtendedElasticsearchVectorStore;
//import org.apache.http.Header;
//import org.apache.http.HttpHost;
//import org.apache.http.message.BasicHeader;
//import org.elasticsearch.client.RestClient;
//import org.opensearch.client.opensearch.OpenSearchClient;
//import org.springframework.ai.embedding.EmbeddingModel;
//import org.springframework.ai.embedding.TokenCountBatchingStrategy;
//import org.springframework.ai.vectorstore.VectorStore;
//import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStoreOptions;
//import org.springframework.ai.vectorstore.elasticsearch.SimilarityFunction;
//import org.springframework.ai.vectorstore.opensearch.OpenSearchVectorStore;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//import java.util.Base64;
//
//@Configuration
//public class ElasticsearchVectorConfig {
//
//    @Value("${spring.elasticsearch.uris}")
//    private String urls;
//    @Value("${spring.elasticsearch.username}")
//    private String username;
//    @Value("${spring.elasticsearch.password}")
//    private String password;
//
//    @Value("${esVectorstore.index-name}")
//    private String indexName;
//    @Value("${esVectorstore.dimensions}")
//    private int dimensions;
//    @Value("${esVectorstore.similarity}")
//    private String similarity;
//    @Value("${esVectorstore.embedding-field-name}")
//    private String embeddingFieldName;
//
//
//
//    @Bean
//    public OpenSearchClient openSearchClient() {
//        RestClient restClient = RestClient.builder(
//                        HttpHost.create("http://localhost:9200"))
//                .build();
//
//        return new OpenSearchClient(new RestClientTransport(
//                restClient, new JacksonJsonpMapper()));
//    }
//
//
//    @Bean
//    public VectorStore vectorStore(OpenSearchClient openSearchClient, EmbeddingModel embeddingModel) {
//        return OpenSearchVectorStore.builder(openSearchClient, embeddingModel)
//                .index("custom-index")                // Optional: defaults to "spring-ai-document-index"
//                .similarityFunction("l2")             // Optional: defaults to "cosinesimil"
//                .useApproximateKnn(true)              // Optional: defaults to false (exact k-NN)
//                .dimensions(1536)                     // Optional: defaults to 1536 or embedding model's dimensions
//                .initializeSchema(true)               // Optional: defaults to false
//                .batchingStrategy(new TokenCountBatchingStrategy()) // Optional: defaults to TokenCountBatchingStrategy
//                .build();
//    }
//
//    // This can be any EmbeddingModel implementation
//    @Bean
//    public EmbeddingModel embeddingModel() {
//        return new OpenAiEmbeddingModel(new OpenAiApi(System.getenv("OPENAI_API_KEY")));
//    }
//
//    @Bean
//    public RestClient restClient() {
//        String[] hosts = urls.split(",");
//        HttpHost[] httpHosts = new HttpHost[hosts.length];
//        for (int i = 0; i < hosts.length; i++) {
//            httpHosts[i] = HttpHost.create(hosts[i]);
//        }
//        String auth = username + ":" + password;
//        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
//        return RestClient.builder(httpHosts)
//                .setDefaultHeaders(new Header[]{
//                        new BasicHeader("Authorization", "Basic " + encodedAuth)
//                })
//                .build();
//    }
//
//    @Bean
//    public ElasticsearchTransport elasticsearchTransport(RestClient restClient) {
//        return new RestClientTransport(
//                restClient, new JacksonJsonpMapper()
//        );
//    }
//
//    @Bean
//    public ElasticsearchClient elasticsearchClient(ElasticsearchTransport transport) {
//        return new ElasticsearchClient(transport);
//    }
//
//    @Bean
//    public VectorStore vectorStore(RestClient restClient, EmbeddingModel embeddingModel) {
//        ElasticsearchVectorStoreOptions options = new ElasticsearchVectorStoreOptions();
//        options.setIndexName(indexName);    // Optional: defaults to "spring-ai-document-index"
//        options.setSimilarity(SimilarityFunction.valueOf(similarity));           // Optional: defaults to COSINE
//        options.setDimensions(dimensions);             // Optional: defaults to model dimensions or 1536
//        options.setEmbeddingFieldName(embeddingFieldName);
//
//        return ExtendedElasticsearchVectorStore.builder(restClient, embeddingModel)
//                .options(options)                     // Optional: use custom options
//                .initializeSchema(true)               // Optional: defaults to false
//                .batchingStrategy(new TokenCountBatchingStrategy()) // Optional: defaults to TokenCountBatchingStrategy
//                .build();
//    }
//
//
//}
