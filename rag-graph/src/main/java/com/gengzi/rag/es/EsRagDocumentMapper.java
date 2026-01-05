package com.gengzi.rag.es;

import com.gengzi.rag.graph.RagGraphDocument;
import com.gengzi.rag.graph.RagGraphDocument.RagGraphChunk;
import com.gengzi.rag.graph.RagGraphDocument.RagGraphEntity;
import com.gengzi.rag.graph.RagGraphDocument.RagGraphEntityRelation;
import com.gengzi.rag.graph.RagGraphDocument.RagGraphMention;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class EsRagDocumentMapper {

    public RagGraphDocument map(Map<String, Object> source, String fallbackId) {
        if (source == null || source.isEmpty()) {
            return null;
        }

        RagGraphDocument document = new RagGraphDocument();
        Map<String, Object> metadata = getMap(source, "metadata");
        String docId = getString(source, "doc_id", "docId", "document_id", "documentId", "id");
        if ((docId == null || docId.isBlank()) && metadata != null) {
            docId = getString(metadata, "documentId", "kbId", "fileId");
        }
        if (docId == null || docId.isBlank()) {
            docId = fallbackId;
        }
        document.setDocId(docId);
        String title = getString(source, "title", "doc_title", "document_title", "docnm_kwd");
        if ((title == null || title.isBlank()) && metadata != null) {
            title = getString(metadata, "documentName", "documentType");
        }
        if (title == null || title.isBlank()) {
            title = getString(source, "title_tks", "title_sm_tks");
        }
        document.setTitle(title);
        String url = getString(source, "url", "doc_url", "document_url");
        if ((url == null || url.isBlank()) && metadata != null) {
            url = getString(metadata, "sourceFileUrl", "image_resource");
        }
        document.setUrl(url);
        String createdAt = getString(source, "created_at", "createdAt", "create_time");
        if ((createdAt == null || createdAt.isBlank()) && metadata != null) {
            createdAt = getString(metadata, "createdAt");
        }
        document.setCreatedAt(parseDate(createdAt));

        List<Map<String, Object>> chunkMaps = getListOfMaps(source, "chunks", "chunk_list", "chunkList");
        if (chunkMaps.isEmpty()) {
            RagGraphChunk chunk = mapChunk(source, docId);
            if (chunk != null) {
                document.getChunks().add(chunk);
            }
            return document;
        }

        for (Map<String, Object> chunkMap : chunkMaps) {
            RagGraphChunk chunk = mapChunk(chunkMap, docId);
            if (chunk != null) {
                document.getChunks().add(chunk);
            }
        }

        return document;
    }

    private RagGraphChunk mapChunk(Map<String, Object> source, String docId) {
        RagGraphChunk chunk = new RagGraphChunk();
        String chunkId = getString(source, "chunk_id", "chunkId", "id", "doc_id");
        Integer index = getInteger(source, "index", "chunk_index", "chunkIndex",
                "position_int", "pageNumber", "page_num_int", "pageNumInt");
        String content = getString(source, "content", "text", "chunk_text",
                "content_sm_ltks", "content_ltks");

        if (chunkId == null || chunkId.isBlank()) {
            if (docId != null && index != null) {
                chunkId = docId + ":" + index;
            } else {
                chunkId = UUID.randomUUID().toString();
            }
        }

        chunk.setChunkId(chunkId);
        chunk.setIndex(index);
        chunk.setContent(content);

        List<Map<String, Object>> mentionMaps = getListOfMaps(source, "mentions", "mention_list", "entity_mentions");
        for (Map<String, Object> mentionMap : mentionMaps) {
            RagGraphMention mention = mapMention(mentionMap, chunkId);
            if (mention != null) {
                chunk.getMentions().add(mention);
            }
        }

        return chunk;
    }

    private RagGraphMention mapMention(Map<String, Object> source, String chunkId) {
        if (source == null || source.isEmpty()) {
            return null;
        }

        RagGraphMention mention = new RagGraphMention();
        String mentionId = getString(source, "mention_id", "mentionId", "id");
        if (mentionId == null || mentionId.isBlank()) {
            mentionId = UUID.randomUUID().toString();
        }
        mention.setMentionId(mentionId);
        mention.setSpan(getString(source, "span", "text", "mention"));
        mention.setConfidence(getDouble(source, "confidence", "score"));
        mention.setChunkId(chunkId);

        RagGraphEntity entity = mapEntity(source);
        mention.setReferredEntity(entity);

        return mention;
    }

    private RagGraphEntity mapEntity(Map<String, Object> source) {
        Map<String, Object> entityMap = getMap(source, "entity", "referred_entity", "referredEntity", "target_entity", "targetEntity", "entity_info");
        RagGraphEntity entity = new RagGraphEntity();
        if (entityMap == null) {
            String entityId = getString(source, "entity_id", "entityId");
            String name = getString(source, "entity_name", "name");
            if ((entityId == null || entityId.isBlank()) && (name == null || name.isBlank())) {
                return null;
            }
            entity.setId(entityId == null || entityId.isBlank() ? UUID.randomUUID().toString() : entityId);
            entity.setName(name);
            entity.setCanonicalName(getString(source, "canonical_name", "canonicalName"));
            entity.setType(getString(source, "type", "entity_type"));
            entity.setSlug(getString(source, "slug"));
        } else {
            entity.setId(getString(entityMap, "id", "entity_id", "entityId"));
            entity.setName(getString(entityMap, "name", "entity_name"));
            entity.setCanonicalName(getString(entityMap, "canonical_name", "canonicalName"));
            entity.setType(getString(entityMap, "type", "entity_type"));
            entity.setSlug(getString(entityMap, "slug"));
            entity.setAliases(getStringList(entityMap, "aliases", "alias"));
        }

        if (entity.getId() == null || entity.getId().isBlank()) {
            if (entity.getName() != null && !entity.getName().isBlank()) {
                entity.setId("ent:" + entity.getName().toLowerCase().replace(" ", "_"));
            } else {
                entity.setId(UUID.randomUUID().toString());
            }
        }

        List<Map<String, Object>> relationMaps = getListOfMaps(entityMap == null ? source : entityMap,
                "relations", "relation_list", "edges");
        for (Map<String, Object> relationMap : relationMaps) {
            RagGraphEntityRelation relation = mapRelation(relationMap);
            if (relation != null) {
                entity.getRelations().add(relation);
            }
        }

        return entity;
    }

    private RagGraphEntityRelation mapRelation(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return null;
        }

        RagGraphEntityRelation relation = new RagGraphEntityRelation();
        relation.setKey(getString(source, "key", "relation", "type"));
        relation.setDescription(getString(source, "description", "desc"));
        relation.setUncertain(getBoolean(source, "uncertain", "is_uncertain"));

        Map<String, Object> targetMap = getMap(source, "target", "target_entity", "to");
        if (targetMap != null) {
            RagGraphEntity target = new RagGraphEntity();
            target.setId(getString(targetMap, "id", "entity_id", "entityId"));
            target.setName(getString(targetMap, "name", "entity_name"));
            target.setCanonicalName(getString(targetMap, "canonical_name", "canonicalName"));
            target.setType(getString(targetMap, "type", "entity_type"));
            target.setSlug(getString(targetMap, "slug"));
            target.setAliases(getStringList(targetMap, "aliases", "alias"));
            if (target.getId() == null || target.getId().isBlank()) {
                if (target.getName() != null && !target.getName().isBlank()) {
                    target.setId("ent:" + target.getName().toLowerCase().replace(" ", "_"));
                } else {
                    target.setId(UUID.randomUUID().toString());
                }
            }
            relation.setTargetEntity(target);
        }

        return relation;
    }

    private String getString(Map<String, Object> source, String... keys) {
        if (source == null) {
            return null;
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof String stringValue && !stringValue.isBlank()) {
                return stringValue;
            }
        }
        return null;
    }

    private Integer getInteger(Map<String, Object> source, String... keys) {
        if (source == null) {
            return null;
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value instanceof String stringValue) {
                try {
                    String trimmed = stringValue.trim();
                    int commaIndex = trimmed.indexOf(',');
                    if (commaIndex > 0) {
                        trimmed = trimmed.substring(0, commaIndex).trim();
                    }
                    if (trimmed.isBlank()) {
                        continue;
                    }
                    return Integer.parseInt(trimmed);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private Double getDouble(Map<String, Object> source, String... keys) {
        if (source == null) {
            return null;
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            if (value instanceof String stringValue) {
                try {
                    return Double.parseDouble(stringValue);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private Boolean getBoolean(Map<String, Object> source, String... keys) {
        if (source == null) {
            return null;
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof Boolean bool) {
                return bool;
            }
            if (value instanceof String stringValue) {
                return Boolean.parseBoolean(stringValue);
            }
        }
        return null;
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ignored) {
            try {
                return java.time.LocalDateTime.parse(value,
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        .toLocalDate();
            } catch (DateTimeParseException ignoredAgain) {
                try {
                    return java.time.OffsetDateTime.parse(value).toLocalDate();
                } catch (DateTimeParseException ignoredThird) {
                    try {
                        return java.time.LocalDateTime.parse(value).toLocalDate();
                    } catch (DateTimeParseException ignoredFourth) {
                        return null;
                    }
                }
            }
        }
    }

    private Map<String, Object> getMap(Map<String, Object> source, String... keys) {
        if (source == null) {
            return null;
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof Map) {
                return (Map<String, Object>) value;
            }
        }
        return null;
    }

    private List<Map<String, Object>> getListOfMaps(Map<String, Object> source, String... keys) {
        if (source == null) {
            return Collections.emptyList();
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof List<?> list) {
                List<Map<String, Object>> results = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map) {
                        results.add((Map<String, Object>) item);
                    }
                }
                return results;
            }
        }
        return Collections.emptyList();
    }

    private List<String> getStringList(Map<String, Object> source, String... keys) {
        if (source == null) {
            return Collections.emptyList();
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof List<?> list) {
                List<String> results = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof String stringValue && !stringValue.isBlank()) {
                        results.add(stringValue);
                    }
                }
                return results;
            }
            if (value instanceof String stringValue && !stringValue.isBlank()) {
                return List.of(stringValue);
            }
        }
        return Collections.emptyList();
    }
}
