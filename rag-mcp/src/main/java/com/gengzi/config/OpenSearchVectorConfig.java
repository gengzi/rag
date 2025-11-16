package com.gengzi.config;





import com.gengzi.memory.vector.opensearch.ExtendedOpenSearchVectorStore;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.core5.function.Factory;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.reactor.ssl.TlsDetails;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.opensearch.OpenSearchVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;


@Configuration
public class OpenSearchVectorConfig {


    @Bean
    public OpenSearchClient openSearchClient() throws URISyntaxException, CertificateException, IOException {
        // 1. 加载 root-ca.pem
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        try (InputStream caInputStream = new ClassPathResource("certs/root-ca.pem").getInputStream()) {
            X509Certificate caCert = (X509Certificate) cf.generateCertificate(caInputStream);
            // 2. 创建只信任这个 CA 的 TrustManager
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            keyStore.setCertificateEntry("opensearch-ca", caCert);

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keyStore);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), null);

            final HttpHost host = new HttpHost("localhost", 9201,"https");
            final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            // Only for demo purposes. Don't specify your credentials in code.
            credentialsProvider.setCredentials(new AuthScope(host), new UsernamePasswordCredentials("admin", "Ragjava001."));
//
            RestClient restClient = RestClient.builder(host)
                    .setHttpClientConfigCallback(builder -> {
//                        final TlsStrategy tlsStrategy = ClientTlsStrategyBuilder.create()
//                                .setSslContext(sslContext).setTlsDetailsFactory(
//                                        sslEngine -> new TlsDetails(sslEngine.getSession(), sslEngine.getApplicationProtocol())
//                                ).build();
//
//                        final PoolingAsyncClientConnectionManager connectionManager = PoolingAsyncClientConnectionManagerBuilder
//                                .create()
//                                .setTlsStrategy(tlsStrategy)
//                                .build();
//                        return builder
//                                .setDefaultCredentialsProvider(credentialsProvider)
//                                .setConnectionManager(connectionManager);
                        builder.setSSLContext(sslContext);
                        return builder.setDefaultCredentialsProvider(credentialsProvider);
                    }).build();

//            final ApacheHttpClient5TransportBuilder builder = ApacheHttpClient5TransportBuilder.builder(host);
//            builder.setHttpClientConfigCallback(httpClientBuilder -> {
//                final TlsStrategy tlsStrategy = ClientTlsStrategyBuilder.create()
//                        .setSslContext(sslContext)
//                        // See https://issues.apache.org/jira/browse/HTTPCLIENT-2219
//                        .setTlsDetailsFactory(new Factory<SSLEngine, TlsDetails>() {
//                            @Override
//                            public TlsDetails create(final SSLEngine sslEngine) {
//                                return new TlsDetails(sslEngine.getSession(), sslEngine.getApplicationProtocol());
//                            }
//                        })
//                        .build();
//
//                final PoolingAsyncClientConnectionManager connectionManager = PoolingAsyncClientConnectionManagerBuilder
//                        .create()
//                        .setTlsStrategy(tlsStrategy)
//                        .build();
//
//                return httpClientBuilder
//                        .setDefaultCredentialsProvider(credentialsProvider)
//                        .setConnectionManager(connectionManager);
//            });

            return new OpenSearchClient(new RestClientTransport(restClient, new JacksonJsonpMapper()));
//            final OpenSearchTransport transport = builder.build();
//            OpenSearchClient client = new OpenSearchClient(transport);
//            return client;
        } catch (KeyManagementException e) {
            throw new RuntimeException(e);
        } catch (KeyStoreException e) {
            throw new RuntimeException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

    }


    @Bean
    public VectorStore openSearchVectorStore(OpenSearchClient openSearchClient, EmbeddingModel embeddingModel) {
        return ExtendedOpenSearchVectorStore.builder(openSearchClient, embeddingModel)
                .index("rag_stroe_mcp")                // Optional: defaults to "spring-ai-document-index"
                .similarityFunction("cosinesimil")             // Optional: defaults to "cosinesimil"
                .useApproximateKnn(true)              // Optional: defaults to false (exact k-NN)
                .dimensions(1024)                     // Optional: defaults to 1536 or embedding model's dimensions
                .initializeSchema(false)               // Optional: defaults to false
                .batchingStrategy(new TokenCountBatchingStrategy()) // Optional: defaults to TokenCountBatchingStrategy
                .build();
    }




}
