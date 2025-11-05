//package com.gengzi.tool.ppt.handler;
//
//import com.gengzi.tool.ppt.enums.XSLFSlideLayoutType;
//import org.springframework.stereotype.Component;
//
//import java.util.EnumMap;
//import java.util.List;
//import java.util.Map;
//import java.util.Optional;
//
//// 处理器工厂
//@Component
//public class SlideHandlerFactory {
//    private final Map<XSLFSlideLayoutType, SlideHandler> handlerMap;
//
//    // 自动注入所有SlideHandler实现并映射到类型
//    public SlideHandlerFactory(List<SlideHandler> handlers) {
//        this.handlerMap = new EnumMap<>(XSLFSlideLayoutType.class);
//        if (handlers.stream().filter(handler -> handler instanceof DefaultSlideHandler).count() == 0) {
//            handlers.add(new DefaultSlideHandler());
//        }
//        handlers.forEach(handler -> {
//            XSLFSlideLayoutType type = determineType(handler);
//            handlerMap.put(type, handler);
//        });
//    }
//
//    private XSLFSlideLayoutType determineType(SlideHandler handler) {
//        if (handler instanceof DefaultSlideHandler) return XSLFSlideLayoutType.TEXT_CONTENT_PAGE;
//        throw new IllegalArgumentException("Unsupported handler type: " + handler.getClass());
//    }
//
//    public SlideHandler getHandler(XSLFSlideLayoutType type) {
//        return Optional.ofNullable(handlerMap.get(type))
//                .orElseThrow(() -> new IllegalArgumentException("No handler for type: " + type));
//    }
//}