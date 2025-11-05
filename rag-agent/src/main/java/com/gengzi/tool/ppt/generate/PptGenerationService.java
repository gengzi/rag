package com.gengzi.tool.ppt.generate;

import com.gengzi.config.FileReadService;
import com.gengzi.controller.AiPPTController;
import com.gengzi.response.BusinessException;
import com.gengzi.tool.ppt.handler.SlideHandler;
import com.gengzi.tool.ppt.model.PlaceholderData;
import com.gengzi.tool.ppt.model.SlideData;
import org.apache.poi.xslf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class PptGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(AiPPTController.class);
    @Autowired
    private FileReadService fileReadService;

    @Autowired
    private SlideHandler slideHandler;
    /**
     * 为幻灯片的占位符填充简单文本（可选，为空时可注释）
     *
     * @param slide   幻灯片对象
     * @param slideData 页内数据
     */
    private void fillPlaceholders(XSLFSlide slide, SlideData slideData) {
        slideHandler.handle(slide, slideData);
//        // 找到所有占位符，逐个进行替换
//        for (XSLFShape shape : slide.getShapes()) {
//            // 占位符名称
//            String shapeName = shape.getShapeName();
//            Map<String, PlaceholderData> data = slideData.getData();
//            if (!data.containsKey(shapeName)) {
//                logger.debug("子版有内容并未进行替换：{}",shapeName);
//            }
//            // 根据元素类型进行对应的替换
//
//        }
    }

    public void generatePPT(String templatePath, String outputPath, List<SlideData> slideDatas) {
        try (XMLSlideShow slideShow = new XMLSlideShow(fileReadService.readFromClasspath(templatePath));
             FileOutputStream fos = new FileOutputStream(outputPath)) {
            if (slideShow.getSlideMasters() != null && slideShow.getSlideMasters().size() > 1) {
                throw new BusinessException( "模板文件异常");
            }
            // 获取模板中的第一个母版
            XSLFSlideMaster master = slideShow.getSlideMasters().get(0);
            // 获取母版包含的所有子版式（布局）
            List<XSLFSlideLayout> layouts = Arrays.stream(master.getSlideLayouts()).toList();
            if (layouts.isEmpty()) {
                throw new BusinessException("模板中未找到子版式");
            }
            for (int page = 0; page < slideDatas.size(); page++) {
                SlideData data = slideDatas.get(page);
                // 根据ppt 的页面顺序获取子版样式和填充内容
                Optional<XSLFSlideLayout> slideLayoutByType = layouts.stream()
                        .filter(layout -> data.getType().getPageType().equals(layout.getName()))
                        .findFirst();
                if (!slideLayoutByType.isPresent()) {
                    throw new BusinessException("未找到匹配的子版式");
                }
                XSLFSlideLayout layout = slideLayoutByType.get();
                // 基于子版式创建新幻灯片
                XSLFSlide slide = slideShow.createSlide(layout);
                // 为幻灯片页填充占位符对应的内容
                fillPlaceholders(slide, data);
            }
            // 保存生成的PPT
            slideShow.write(fos);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}