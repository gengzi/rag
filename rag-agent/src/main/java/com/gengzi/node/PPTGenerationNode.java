package com.gengzi.node;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.gengzi.dao.PptMaster;
import com.gengzi.dao.repository.PptMasterRepository;
import com.gengzi.response.BusinessException;
import com.gengzi.tool.ppt.config.AiPPTConfig;
import com.gengzi.tool.ppt.dto.ParseResult;
import com.gengzi.tool.ppt.generate.AiPPTContentGenerationService;
import com.gengzi.tool.ppt.generate.PptGenerationService;
import com.gengzi.tool.ppt.model.PptLayout;
import com.gengzi.tool.ppt.model.PptMasterModel;
import com.gengzi.tool.ppt.model.SlideData;
import com.gengzi.tool.ppt.parser.PptMasterParser;
import com.gengzi.tool.ppt.util.PptOutlineParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 大纲生成节点
 */
@Component
public class PPTGenerationNode implements NodeAction {

    private static final Logger logger = LoggerFactory.getLogger(PPTGenerationNode.class);
    @Autowired
    private AiPPTConfig aiPPTConfig;


    @Autowired
    @Qualifier("deepseekChatClientNoRag")
    private ChatClient chatClient;

    @Autowired
    private AiPPTContentGenerationService aiPPTContentGenerationService;

    @Autowired
    private PptGenerationService pptGenerationService;
    @Autowired
    private PptMasterParser pptMasterParser;

    @Autowired
    private PptMasterRepository pptMasterRepository;


    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {

        // 获取反馈信息
        Map<String, Object> feedBackData = state.humanFeedback().data();
        final String feedback = (String) feedBackData.getOrDefault("feedback", "");
       logger.info("用户反馈：{}", feedback);
        // TODO 调用大模型得到风格对应的 母版id，进行内容生产

        logger.info("开始执行ppt生成");
//        String feedback = state.value("human_feedback", "");
//        logger.info("用户反馈：{}", feedback);
        // 获取输出的ppt大纲，进行ppt生成任务
        String outlineGenNodeContent = state.value("outlineGenNode_content", "");
        String pptMasterName = state.value("pptMasterName", "母版类型-1.potx");
        logger.info("大纲内容：{}", outlineGenNodeContent);
        // 为空就提示用户异常
        if (StrUtil.isBlank(outlineGenNodeContent)) {
            // TODO 还不能抛出异常，会导致流程卡主
//            throw new BusinessException("outlineGenNode_content is not blank");
        }
        // 通过正则表达式核验大纲内容是否符合格式,并返回大纲各个节点信息
        ParseResult parseResult = PptOutlineParser.validateAndExtract(outlineGenNodeContent);
        if (!parseResult.isValid()) {
            logger.error("大纲内容不符合要求:{}", parseResult.getErrorMsg());
            // TODO 考虑agent设计时 重新生成（正常不应该错误）
//            throw new BusinessException("大纲内容不符合要求");
            Flux<GraphResponse<StreamingOutput>> responseFlux = Flux.just(
                    new StreamingOutput(" * \uD83D\uDCA5 生成ppt失败，请稍后再试... \n", "pptGenNode", state)
            ).map(GraphResponse::of);
            return Map.of("pptGenNode", responseFlux);
        }
        Flux<GraphResponse<StreamingOutput>> responseFlux = Flux.just(
                new StreamingOutput(" * \uD83E\uDDE0 生成ppt中，请稍候... \n", "pptGenNode", state)
        ).map(GraphResponse::of);


        Mono<GraphResponse<StreamingOutput>> gen = Mono.fromCallable(() -> {
//            // TODO 测试异常是否被捕获并发送给前端
//            if(true){
//                throw  new BusinessException("异常测试");
//            }
            // 通过母版解析
            List<PptMaster> pptMasterByName = pptMasterRepository.findPptMasterByName(pptMasterName);
            if (Objects.isNull(pptMasterByName) || pptMasterByName.isEmpty() || pptMasterByName.size() > 1) {
                throw new BusinessException("数据异常，生成失败");
            }
            PptMaster pptMaster = pptMasterByName.get(0);

            PptMasterModel pptMasterModel = new PptMasterModel();
            pptMasterModel.setMasterName(pptMaster.getName());
            pptMasterModel.setLayouts(JSONUtil.toList(pptMaster.getPptLayout(), PptLayout.class));

            // 将解析的母版信息与大纲信息进行匹配，分别针对不同的页面，生成对应的内容
            List<SlideData> slideDatas = aiPPTContentGenerationService.generateContent(pptMasterModel, parseResult);

            String fileName = parseResult.getTotalTitle() + ".pptx";
            pptGenerationService.generatePPT("ppt/" + pptMasterName, "F:\\baidu\\" + fileName, slideDatas);

            StreamingOutput streamingOutput = new StreamingOutput(" * ✅ 已完成 \n", "pptGenNode", state);
            return GraphResponse.of(streamingOutput);
        }).subscribeOn(Schedulers.boundedElastic());

        Flux<GraphResponse<StreamingOutput>> result = responseFlux.concatWith(gen).concatWith(Mono.fromCallable(() -> {
            Map<String, String> filePath = Map.of("file_path", "F:\\baidu\\output.pptx");
            return GraphResponse.done(filePath);
        }));
        return Map.of("pptGenNode", result);
    }
}
