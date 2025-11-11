package com.gengzi.response;

import com.gengzi.dto.RagChatMessage;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ConversationDetailsResponse {
    @Size(max = 64)
    String id;
    Long createTime;
    LocalDateTime createDate;
    Long updateTime;
    LocalDateTime updateDate;
    @NotNull
    @Size(max = 32)
    String dialogId;
    @Size(max = 255)
    String name;
    List<RagChatMessage> message;
    String reference;
    @Size(max = 255)
    String userId;
    @NotNull
    @Size(max = 64)
    String knowledgebaseId;




}
