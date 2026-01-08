package com.gengzi.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChunkHit {
    private String chunkId;
    private String content;
    private List<String> entityNames;
    private List<String> communityIds;
}
