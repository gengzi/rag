package com.gengzi.repository.neo4j;

import com.gengzi.model.dto.ChunkHit;

import java.util.List;

public interface ChunkRepositoryCustom {
    List<ChunkHit> findLocalChunkHitsDepth1(List<String> entities, int limit);

    List<ChunkHit> findLocalChunkHitsDepth2(List<String> entities, int limit);

    List<ChunkHit> findGlobalChunkHits(List<String> keywords, int limit);
}
