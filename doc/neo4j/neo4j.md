

```sql
// 1. 唯一性约束 (确保不会重复创建同一个文档或实体)
CREATE CONSTRAINT doc_id_unique IF NOT EXISTS FOR (d:Document) REQUIRE d.doc_id IS UNIQUE;
CREATE CONSTRAINT chunk_id_unique IF NOT EXISTS FOR (c:Chunk) REQUIRE c.chunk_id IS UNIQUE;
CREATE CONSTRAINT entity_id_unique IF NOT EXISTS FOR (e:Entity) REQUIRE e.id IS UNIQUE;

// 2. 加速查找 (Mentions 不需要唯一，但需要查得快)
CREATE INDEX mention_span_idx IF NOT EXISTS FOR (m:Mention) ON (m.span);
CREATE INDEX entity_name_idx IF NOT EXISTS FOR (e:Entity) ON (e.name);
```
