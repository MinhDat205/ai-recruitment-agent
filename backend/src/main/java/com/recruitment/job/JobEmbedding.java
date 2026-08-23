package com.recruitment.job;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.generator.EventType;

// job_id la UUID thuong, khong dung @OneToOne - tranh lazy-loading ngoai transaction (open-in-view:
// false), cung ly do voi Resume/ResumeParsedData. KHONG map cot embedding: dung tien le
// ResumeParsedData.java - vector(1536) chi doc/ghi qua native query o JobEmbeddingRepository. Da xac
// minh o Plan Mode (doc bytecode that cua PgVectorStore, spring-ai-pgvector-store-2.0.0.jar): ngay
// ca Spring AI cung khong bind PGvector qua Hibernate, ma dung JdbcTemplate rieng - Hibernate khong
// co JdbcType/UserType dang ky san cho kieu vector, tu them mot cai vao day la chua kiem chung duoc.
// Vi vay embedding CHI di qua String da format san ('[0.1,0.2,...]', xem EmbeddingTextFormat) +
// CAST(... AS vector) trong SQL, khong bao gio la field cua entity nay.
@Entity
@Table(name = "job_embeddings")
@Getter
@Setter
@NoArgsConstructor
public class JobEmbedding {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "job_id", nullable = false, unique = true)
    private UUID jobId;

    @Column(nullable = false)
    private String model;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
