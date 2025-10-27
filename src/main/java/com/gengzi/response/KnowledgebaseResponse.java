package com.gengzi.response;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Value;

import java.io.Serializable;
import java.time.Instant;

/**
 * DTO for {@link com.gengzi.dao.Knowledgebase}
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class KnowledgebaseResponse implements Serializable {
    @Size(max = 32)
    String id;
    Long createTime;
    Instant createDate;
    Long updateTime;
    Instant updateDate;
    String avatar;
    @NotNull
    @Size(max = 128)
    String name;
    @Size(max = 32)
    String language;
    String description;
    @NotNull
    @Size(max = 32)
    String createdBy;
    @NotNull
    Integer docNum;
    @NotNull
    Integer tokenNum;
    @NotNull
    Integer chunkNum;
    @Size(max = 1)
    String status;
}