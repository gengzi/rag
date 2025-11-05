package com.gengzi.tool.ppt.generate;


import cn.hutool.json.JSONUtil;
import com.gengzi.tool.ppt.config.AiPPTConfig;
import com.gengzi.tool.ppt.dto.AiPPTPageGenerate;
import com.gengzi.tool.ppt.dto.AiPPTPlaceholder;
import com.gengzi.tool.ppt.dto.ParseResult;
import com.gengzi.tool.ppt.enums.XSLFSlideLayoutType;
import com.gengzi.tool.ppt.model.PptLayout;
import com.gengzi.tool.ppt.model.PptMasterModel;
import com.gengzi.tool.ppt.model.PptPlaceholder;
import com.gengzi.tool.ppt.model.SlideData;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Service
public class AiPPTContentGenerationService {


    @Autowired
    @Qualifier("deepseekChatClientNoRag")
    private ChatClient chatClient;

    @Autowired
    private AiPPTConfig aiPPTConfig;

    /**
     * 根据母版的页面类型和页面内容，和大纲内容，通过llm 生成ppt每页的内容
     *
     * @param pptMasterModel
     * @param result
     * @return
     */
    public List<SlideData> generateContent(PptMasterModel pptMasterModel, ParseResult result) {
        LinkedList<SlideData> slideData = new LinkedList<>();
        // 首页生成
        String totalTitle = result.getTotalTitle();
        PptLayout homePage = pptMasterModel.getLayoutByName(XSLFSlideLayoutType.HOME_PAGE.getPageType());
        List<PptPlaceholder> placeholders = homePage.getPlaceholders();
        AiPPTPageGenerate aiPPTPageGenerate = new AiPPTPageGenerate();
        aiPPTPageGenerate.setPageType(XSLFSlideLayoutType.HOME_PAGE.getPageName());
        aiPPTPageGenerate.setOutlineInfo(Map.of("totalTitle", totalTitle));
        LinkedList<AiPPTPlaceholder> aiPPTPlaceholders = new LinkedList<>();
        for (PptPlaceholder placeholder : placeholders) {
            AiPPTPlaceholder aiPPTPlaceholder = new AiPPTPlaceholder();
            aiPPTPlaceholder.setId(placeholder.getShapeName());
            aiPPTPlaceholder.setName(placeholder.getDescriptionTitle());
            aiPPTPlaceholder.setDescription(placeholder.getDescription());
            aiPPTPlaceholders.add(aiPPTPlaceholder);
        }
        aiPPTPageGenerate.setPlaceholders(aiPPTPlaceholders);

        String content = chatClient.prompt().system(aiPPTConfig.getPageGenPrompt())
                .user(JSONUtil.toJsonStr(aiPPTPageGenerate))
                .call().content();


        if (JSONUtil.isTypeJSONObject(content)) {
            SlideData data = new SlideData();
            data.setType(XSLFSlideLayoutType.HOME_PAGE);
            Map bean = JSONUtil.toBean(content, Map.class);
            data.setData(bean);
            slideData.add(data);
        }

        // TODO 先简单实现下，后续完善

        return slideData;


    }


}
