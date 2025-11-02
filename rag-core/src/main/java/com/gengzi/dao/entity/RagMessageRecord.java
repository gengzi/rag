package com.gengzi.dao.entity;


import cn.hutool.json.JSONUtil;
import lombok.Data;

import java.util.List;

/**
 * rag消息记录实体
 */
@Data
public class RagMessageRecord {

    List<RagChatMessage> chatMessages;


    public String toJsonByMessageRecord(){
        return JSONUtil.toJsonStr(this.chatMessages);
    }

}
