package com.gengzi.tool.ppt.handler;

import com.gengzi.tool.ppt.model.SlideData;
import org.apache.poi.xslf.usermodel.XSLFSlide;

/**
 * 幻灯片处理器接口
 */
public interface SlideHandler {
    void handle(XSLFSlide slide, SlideData slideData);
}