package com.gengzi.model.graph;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class RagGraphDocument {

    private String docId;
    private String title;
    private String url;
    private LocalDate createdAt;
    private final List<RagGraphChunk> chunks = new ArrayList<>();

    public String getDocId() {
        return docId;
    }

    public void setDocId(String docId) {
        this.docId = docId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public LocalDate getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDate createdAt) {
        this.createdAt = createdAt;
    }

    public List<RagGraphChunk> getChunks() {
        return chunks;
    }

    public static class RagGraphChunk {
        private String chunkId;
        private Integer index;
        private String content;
        private final List<RagGraphMention> mentions = new ArrayList<>();

        public String getChunkId() {
            return chunkId;
        }

        public void setChunkId(String chunkId) {
            this.chunkId = chunkId;
        }

        public Integer getIndex() {
            return index;
        }

        public void setIndex(Integer index) {
            this.index = index;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public List<RagGraphMention> getMentions() {
            return mentions;
        }
    }

    public static class RagGraphMention {
        private String mentionId;
        private String span;
        private Double confidence;
        private String chunkId;
        private RagGraphEntity referredEntity;

        public String getMentionId() {
            return mentionId;
        }

        public void setMentionId(String mentionId) {
            this.mentionId = mentionId;
        }

        public String getSpan() {
            return span;
        }

        public void setSpan(String span) {
            this.span = span;
        }

        public Double getConfidence() {
            return confidence;
        }

        public void setConfidence(Double confidence) {
            this.confidence = confidence;
        }

        public String getChunkId() {
            return chunkId;
        }

        public void setChunkId(String chunkId) {
            this.chunkId = chunkId;
        }

        public RagGraphEntity getReferredEntity() {
            return referredEntity;
        }

        public void setReferredEntity(RagGraphEntity referredEntity) {
            this.referredEntity = referredEntity;
        }
    }

    public static class RagGraphEntity {
        private String id;
        private String name;
        private String canonicalName;
        private String type;
        private String slug;
        private List<String> aliases = new ArrayList<>();
        private final List<RagGraphEntityRelation> relations = new ArrayList<>();

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getCanonicalName() {
            return canonicalName;
        }

        public void setCanonicalName(String canonicalName) {
            this.canonicalName = canonicalName;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getSlug() {
            return slug;
        }

        public void setSlug(String slug) {
            this.slug = slug;
        }

        public List<String> getAliases() {
            return aliases;
        }

        public void setAliases(List<String> aliases) {
            this.aliases = aliases == null ? new ArrayList<>() : new ArrayList<>(aliases);
        }

        public List<RagGraphEntityRelation> getRelations() {
            return relations;
        }
    }

    public static class RagGraphEntityRelation {
        private String key;
        private String description;
        private Boolean uncertain;
        private RagGraphEntity targetEntity;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Boolean getUncertain() {
            return uncertain;
        }

        public void setUncertain(Boolean uncertain) {
            this.uncertain = uncertain;
        }

        public RagGraphEntity getTargetEntity() {
            return targetEntity;
        }

        public void setTargetEntity(RagGraphEntity targetEntity) {
            this.targetEntity = targetEntity;
        }
    }
}