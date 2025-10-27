package com.gengzi.utils;

import cn.hutool.core.util.IdUtil;
import jakarta.validation.constraints.Size;

public class IdUtils {

    public static String generate() {
        return IdUtil.simpleUUID();
    }


    public static String generateChatId() {
        long timeMillis = System.currentTimeMillis();
        return String.valueOf(timeMillis);
    }

    public static String generateKnowledgeId() {
        return "k_" + IdUtil.simpleUUID();
    }

    public static String generateDocumentId() {
        return "d_" + IdUtil.simpleUUID();
    }

    public static String userId() {
        return "u_" + IdUtil.simpleUUID();
    }

    public static String generateChunkImagId() {
        return "c_img" + IdUtil.simpleUUID();
    }
}
