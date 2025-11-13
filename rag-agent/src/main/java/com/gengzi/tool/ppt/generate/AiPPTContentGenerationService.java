package com.gengzi.tool.ppt.generate;


import cn.hutool.json.JSONUtil;
import com.gengzi.tool.ppt.config.AiPPTConfig;
import com.gengzi.tool.ppt.dto.*;
import com.gengzi.tool.ppt.enums.XSLFSlideLayoutType;
import com.gengzi.tool.ppt.model.PptLayout;
import com.gengzi.tool.ppt.model.PptMasterModel;
import com.gengzi.tool.ppt.model.PptPlaceholder;
import com.gengzi.tool.ppt.model.SlideData;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service
public class AiPPTContentGenerationService {


    @Autowired
    @Qualifier("deepseekChatClientNoRag")
    private ChatClient chatClient;

    @Autowired
    private AiPPTConfig aiPPTConfig;

    @NotNull
    private static LinkedList<AiPPTPlaceholder> getAiPPTPlaceholders(List<PptPlaceholder> placeholders) {
        LinkedList<AiPPTPlaceholder> aiPPTPlaceholders = new LinkedList<>();
        for (PptPlaceholder placeholder : placeholders) {
            AiPPTPlaceholder aiPPTPlaceholder = new AiPPTPlaceholder();
            aiPPTPlaceholder.setId(placeholder.getShapeName());
            aiPPTPlaceholder.setName(placeholder.getDescriptionTitle());
            aiPPTPlaceholder.setDescription(placeholder.getDescription());
            aiPPTPlaceholders.add(aiPPTPlaceholder);
        }
        return aiPPTPlaceholders;
    }

    /**
     * 根据母版的页面类型和页面内容，和大纲内容，通过llm 生成ppt每页的内容
     *
     * @param pptMasterModel
     * @param result
     * @return
     */
    public List<SlideData> generateContent(PptMasterModel pptMasterModel, ParseResult result) {
        LinkedList<SlideData> slideDatas = new LinkedList<>();
//        // 循环所有的页面类型，进行页面内容的生成
//        List<XSLFSlideLayoutType> collect = Arrays.stream(XSLFSlideLayoutType.values()).collect(Collectors.toList());
//        for (XSLFSlideLayoutType layoutType : collect) {
//        }

        // 首页
        PptLayout homePage = pptMasterModel.getLayoutByType(XSLFSlideLayoutType.HOME_PAGE);
        SlideData homePageCreate = homePageCreate(homePage, result);
        slideDatas.add(homePageCreate);
        // 目录页
        PptLayout cataloguePage = pptMasterModel.getLayoutByType(XSLFSlideLayoutType.CATALOGUE_PAGE);
        List<String> catalogues = result.getChapters().stream()
                .sorted(Comparator.comparingInt(Chapter::getChapterNum))
                .map(chapter -> chapter.getChapterNum() + " " + chapter.getChapterTitle())
                .collect(Collectors.toList());
        SlideData cataloguePageCreate = cataloguePageCreate(cataloguePage, catalogues);
        slideDatas.add(cataloguePageCreate);
        // 章节页面和内容页生成
        List<SlideData> contentPageCreate = catalogueAndContentPageCreate(pptMasterModel, result);
        slideDatas.addAll(contentPageCreate);
        // 结尾页，默认添加不做生成
        SlideData endingPageCreate = endingPageCreate();
        slideDatas.add(endingPageCreate);
        return slideDatas;
    }

    private SlideData endingPageCreate() {
        SlideData data = new SlideData();
        data.setType(XSLFSlideLayoutType.ENDING_PAGE);
        data.setData(Map.of());
        return data;
    }

    private void test(PptMasterModel pptMasterModel, ParseResult result, LinkedList<SlideData> slideData) {
        // 还是分页面类型来生成
        for (PptLayout pptLayout : pptMasterModel.getLayouts()) {
            XSLFSlideLayoutType layoutType = pptLayout.getLayoutType();
            switch (layoutType) {
                case HOME_PAGE:
                    // 只占一页数据
//                    homePageCreate(pptLayout, result, slideData);
                    break;
                case CATALOGUE_PAGE:
                    // 目录页，只占一页数据
//                    cataloguePageCreate(pptLayout, result, slideData);
                    break;
                case CATALOGUE_TITLE_PAGE:
                    // 小结页，可以为多页

                    break;
                case TEXT_CONTENT_PAGE:
                    // 小结内容页
                    break;

                case ENDING_PAGE:
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * TODO 要加入重试机制
     * 如果生成的内容格式不符合要求，则重试
     *
     * @param pptLayout
     * @param result
     * @return
     */
    private SlideData homePageCreate(PptLayout pptLayout, ParseResult result) {
        String totalTitle = result.getTotalTitle();
        List<PptPlaceholder> placeholders = pptLayout.getPlaceholders();
        AiPPTPageGenerate aiPPTPageGenerate = new AiPPTPageGenerate();
        aiPPTPageGenerate.setPageType(XSLFSlideLayoutType.HOME_PAGE.getPageName());
        aiPPTPageGenerate.setOutlineInfo(Map.of("totalTitle", totalTitle));
        LinkedList<AiPPTPlaceholder> aiPPTPlaceholders = getAiPPTPlaceholders(placeholders);
        aiPPTPageGenerate.setPlaceholders(aiPPTPlaceholders);

        String content = chatClient.prompt().system(aiPPTConfig.getPageGenPrompt())
                .user(JSONUtil.toJsonStr(aiPPTPageGenerate))
                .call().content();
        SlideData data = new SlideData();
        if (JSONUtil.isTypeJSONObject(content)) {
            data.setType(XSLFSlideLayoutType.HOME_PAGE);
            Map bean = JSONUtil.toBean(content, Map.class);
            data.setData(bean);
            return data;
        }
        return data;
    }

    /**
     * TODO 要加入重试机制
     * 如果生成的内容格式不符合要求，则重试
     *
     * @param pptLayout
     * @return
     */
    private SlideData cataloguePageCreate(PptLayout pptLayout, List<String> catalogues) {
        List<PptPlaceholder> placeholders = pptLayout.getPlaceholders();
        AiPPTPageGenerate aiPPTPageGenerate = new AiPPTPageGenerate();
        aiPPTPageGenerate.setPageType(XSLFSlideLayoutType.CATALOGUE_PAGE.getPageName());
        aiPPTPageGenerate.setOutlineInfo(Map.of("chapterTitles", catalogues));
        LinkedList<AiPPTPlaceholder> aiPPTPlaceholders = getAiPPTPlaceholders(placeholders);
        aiPPTPageGenerate.setPlaceholders(aiPPTPlaceholders);

        String content = chatClient.prompt().system(aiPPTConfig.getPageGenPrompt())
                .user(JSONUtil.toJsonStr(aiPPTPageGenerate))
                .call().content();
        SlideData data = new SlideData();
        if (JSONUtil.isTypeJSONObject(content)) {
            data.setType(XSLFSlideLayoutType.CATALOGUE_PAGE);
            Map bean = JSONUtil.toBean(content, Map.class);
            data.setData(bean);
            return data;
        }
        return data;
    }

    /**
     * TODO 要加入重试机制
     * 如果生成的内容格式不符合要求，则重试
     *
     * @return
     */
    private List<SlideData> catalogueAndContentPageCreate(PptMasterModel pptMasterModel, ParseResult result) {
        LinkedList<SlideData> slideDatas = new LinkedList<>();
        PptLayout chapterLayout = pptMasterModel.getLayoutByType(XSLFSlideLayoutType.CATALOGUE_TITLE_PAGE);
        PptLayout textContentLayOut = pptMasterModel.getLayoutByType(XSLFSlideLayoutType.TEXT_CONTENT_PAGE);
        List<Chapter> chapters = result.getChapters();
        for (Chapter chapter : chapters) {
            getCataloguePageData(chapterLayout, chapter, slideDatas);
            // 小结内容页
            List<Section> sections = chapter.getSections();
            for (Section section : sections) {
                getSummaryPageData(textContentLayOut, section, slideDatas);
            }
        }
        return slideDatas;
    }

    private void getSummaryPageData(PptLayout pptLayout, Section section, LinkedList<SlideData> slideDatas) {
        List<PptPlaceholder> placeholders = pptLayout.getPlaceholders();
        AiPPTPageGenerate aiPPTPageGenerate = new AiPPTPageGenerate();
        aiPPTPageGenerate.setPageType(XSLFSlideLayoutType.TEXT_CONTENT_PAGE.getPageName());
        aiPPTPageGenerate.setOutlineInfo(Map.of("summaryTitle", section.getSectionNum() + " " + section.getSectionTitle()));
        LinkedList<AiPPTPlaceholder> aiPPTPlaceholders = getAiPPTPlaceholders(placeholders);
        aiPPTPageGenerate.setPlaceholders(aiPPTPlaceholders);
        String content = chatClient.prompt().system(aiPPTConfig.getPageGenPrompt())
                .user(JSONUtil.toJsonStr(aiPPTPageGenerate))
                .call().content();
        SlideData data = new SlideData();
        if (JSONUtil.isTypeJSONObject(content)) {
            data.setType(XSLFSlideLayoutType.TEXT_CONTENT_PAGE);
            Map bean = JSONUtil.toBean(content, Map.class);
            data.setData(bean);
            slideDatas.add(data);
        }
    }

    private void getCataloguePageData(PptLayout pptLayout, Chapter chapter, LinkedList<SlideData> slideDatas) {
        List<PptPlaceholder> placeholders = pptLayout.getPlaceholders();
        AiPPTPageGenerate aiPPTPageGenerate = new AiPPTPageGenerate();
        aiPPTPageGenerate.setPageType(XSLFSlideLayoutType.CATALOGUE_TITLE_PAGE.getPageName());
        aiPPTPageGenerate.setOutlineInfo(Map.of("chapterTitle", chapter.getChapterNum() + " " + chapter.getChapterTitle()));
        LinkedList<AiPPTPlaceholder> aiPPTPlaceholders = getAiPPTPlaceholders(placeholders);
        aiPPTPageGenerate.setPlaceholders(aiPPTPlaceholders);
        String content = chatClient.prompt().system(aiPPTConfig.getPageGenPrompt())
                .user(JSONUtil.toJsonStr(aiPPTPageGenerate))
                .call().content();
        SlideData data = new SlideData();
        if (JSONUtil.isTypeJSONObject(content)) {
            data.setType(XSLFSlideLayoutType.CATALOGUE_TITLE_PAGE);
            Map bean = JSONUtil.toBean(content, Map.class);
            data.setData(bean);
            slideDatas.add(data);
        }
    }


}
