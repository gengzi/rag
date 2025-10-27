package com.gengzi.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * DTO for {@link com.gengzi.dao.Knowledgebase}
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class KnowledgebaseCreateReq implements Serializable {
    @NotNull
    @Size(max = 128)
    String name;
    String description;
}