package com.gengzi.response;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * DTO for {@link com.gengzi.dao.Conversation}
 */
@Data
@Builder
public class ConversationResponse implements Serializable {
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
    @NotNull
    @Size(max = 64)
    String knowledgebaseId;
}