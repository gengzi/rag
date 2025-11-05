package com.gengzi.tool.ppt.handler;

import com.gengzi.tool.ppt.model.PlaceholderData;
import com.gengzi.tool.ppt.model.SlideData;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 首页处理器
 */
@Service
public class DefaultSlideHandler implements SlideHandler {
    private static final Logger logger = LoggerFactory.getLogger(DefaultSlideHandler.class);

    @Override
    public void handle(XSLFSlide slide, SlideData slideData) {
        for (XSLFShape shape : slide.getShapes()) {
            // 占位符名称
            String shapeName = shape.getShapeName();
            Map<String, String> data = slideData.getData();
            if (!data.containsKey(shapeName)) {
                logger.debug("子版有内容并未进行替换：{}",shapeName);
            }
            //TODO 根据元素类型进行对应的替换
            String placeholderData = data.get(shapeName);
            XSLFTextShape textBox = (XSLFTextShape) shape;
            String text = textBox.getText(); // 获取文本框内容
            String textBoxName = textBox.getShapeName(); // 获取文本框名称
            textBox.setText(placeholderData);
        }

    }

}