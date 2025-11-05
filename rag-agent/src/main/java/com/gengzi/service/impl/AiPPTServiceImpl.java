package com.gengzi.service.impl;

import com.gengzi.request.AiPPTGenerateReq;
import com.gengzi.service.AiPPTService;
import com.gengzi.tool.ppt.config.AiPPTConfig;
import com.gengzi.tool.ppt.dto.ParseResult;
import com.gengzi.tool.ppt.enums.XSLFSlideLayoutType;
import com.gengzi.tool.ppt.generate.AiPPTContentGenerationService;
import com.gengzi.tool.ppt.generate.PptGenerationService;
import com.gengzi.tool.ppt.handler.DefaultSlideHandler;
import com.gengzi.tool.ppt.model.PptMasterModel;
import com.gengzi.tool.ppt.model.SlideData;
import com.gengzi.tool.ppt.parser.PptMasterParser;
import com.gengzi.tool.ppt.util.PptOutlineParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;

@Service
public class AiPPTServiceImpl implements AiPPTService {

    private static final Logger logger = LoggerFactory.getLogger(AiPPTServiceImpl.class);
    @Autowired
    private PptMasterParser pptMasterParser;

    @Autowired
    private PptGenerationService pptGenerationService;

    @Autowired
    @Qualifier("deepseekChatClientNoRag")
    private ChatClient chatClient;


    @Autowired
    private AiPPTConfig aiPPTConfig;

    @Autowired
    private AiPPTContentGenerationService aiPPTContentGenerationService;

    @Override
    public void generatePPT(AiPPTGenerateReq req) throws Exception {
        // 通过母版解析
        PptMasterModel pptMasterModel = pptMasterParser.parseMaster("ppt/母版11.potx");
        // TODO 入库
        // 构造内容
        // TODO 到时候需要流示输出
        // 获取关于问题的ppt大纲
        String outline = chatClient.prompt().system(aiPPTConfig.getOutlinePrompt())
                .user(req.getQuery())
                .call().content();
        // 通过正则表达式核验大纲内容是否符合格式,并返回大纲各个节点信息
        ParseResult parseResult = PptOutlineParser.validateAndExtract(outline);
        if(!parseResult.isValid()){
            logger.error("大纲内容不符合要求:{}",parseResult.getErrorMsg());
            // TODO 考虑agent设计时 重新生成（正常不应该错误）
            return;
        }

        // 将解析的母版信息与大纲信息进行匹配，分别针对不同的页面，生成对应的内容
        List<SlideData> slideDatas = aiPPTContentGenerationService.generateContent(pptMasterModel, parseResult);

        pptGenerationService.generatePPT("ppt/母版11.potx", "F:\\baidu\\output.pptx", slideDatas);
    }
}
