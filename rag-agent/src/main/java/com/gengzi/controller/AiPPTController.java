package com.gengzi.controller;

import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.gengzi.request.AiPPTGenerateReq;
import com.gengzi.service.AiPPTService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/aippt")
public class AiPPTController {

    private static final Logger logger = LoggerFactory.getLogger(AiPPTController.class);


    @Autowired
    private AiPPTService aiPPTService;
    /**
     * 生成
     *
     * @return
     * @throws GraphRunnerException
     */
    @PostMapping(value = "/generate")
    public void expand(@RequestBody AiPPTGenerateReq req) throws Exception {


        aiPPTService.generatePPT(req);

//        RunnableConfig runnableConfig = RunnableConfig.builder().threadId(threadId).build();
//        Map<String, Object> objectMap = new HashMap<>();
//        objectMap.put("query", query);
//        objectMap.put("expandernumber", expanderNumber);
//        Optional<OverAllState> invoke = this.compiledGraph.call(objectMap, runnableConfig);
//        return invoke.map(OverAllState::data).orElse(new HashMap<>());
    }
}