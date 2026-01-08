package com.gengzi.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GraphQueryResponse {
    private String question;
    private String searchType;
    private List<String> entities;
    private List<String> keywords;
    private List<ChunkHit> chunks;
}
