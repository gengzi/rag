package com.gengzi.response;

import com.gengzi.dao.entity.RagChatMessage;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.Value;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for {@link com.gengzi.dao.Conversation}
 */
@Data
public class ConversationDetailsResponse implements Serializable {
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