// src/test/java/com/example/demo/DemoApplicationTests.java


import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Map;


@SpringBootTest
class DemoApplicationTests {

    @Autowired
    private AppProperties appProperties;
    @Autowired
    private Environment environment;

    @Test
    void testApplicationYmlLoadedFromTestResources() {

        String systemPrompt = appProperties.getSystem_prompt();
        Map<String, String> bean = JSONUtil.toBean(systemPrompt, Map.class);
        String value = bean.get("default");
        System.out.println(value);

        String nextStepPrompt = appProperties.getNext_step_prompt();
        Map<String, String> bean2 = JSONUtil.toBean(nextStepPrompt, Map.class);
        String value2 = bean2.get("default");
        System.out.println(value2);

    }

    @Configuration
    @EnableConfigurationProperties(AppProperties.class)
    static class TestConfig {
    }

//    @Test
//    void testServerPortIs8081() {
//        String port = environment.getProperty("server.port");
//        assertThat(port).isEqualTo("8081");
//    }
}