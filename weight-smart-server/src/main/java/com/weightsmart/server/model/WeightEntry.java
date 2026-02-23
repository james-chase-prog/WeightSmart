package com.weightsmart.server.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime; //datetime datatype functions
import jakarta.validation.constraints.Min; //validation construct
import jakarta.validation.constraints.PastOrPresent; //validation construct
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/*
 * Weight Entity
 * Represents a single weight measurement recorded by a user
 *
 * Architecture Role:
 * Designed for Offline-First Synchronization strategy, including audit fields managed by Hibernate to track data lineage.
 * <a href ="https://docs.hibernate.org/orm/current/userguide/html_single/#pc.html"> Hibernate Documentation</a>
 *
 * Key Concepts & Documentation:
 * B-Tree Indexing: The {@code unique=true} attribute automatically creates B-Tree indexes
 * for O(log n) lookup performance. <a href="https://www.postgresql.org/docs/current/sql-createindex.html">Reference: PostgreSQL Indexes</a>
 * Lombok: Used for generation of getter, setter, toString, Builder, NoArgsConstructor, AllArgsConstructor methods
 * <a href="https://projectlombok.org/features/.html">Reference: Lombok Documentation</a>
 * Jarkarta Persistence: Provides access to Entity , Table, and ManytoOne. Provides of java objects to records into PostgreSQL. Table used to create composite B-tree.
 * <a href="https://jakarta.ee/specifications/persistence/3.0/apidocs/jakarta.persistence/jakarta/persistence/package-summary.html"> Reference= Jakarta Persistence Documentation</a>
 * Jakarta Validation: Provides validation methods @Min, @PastOrPresent for simplified data attribute validation
 * <a href="https://beanvalidation.org/2.0/spec/.html">Jakarta Bean Validation 2.0 Documentation</a>
 *
 *
 * @author James Chase
 * @version 1.0
 * @since 2026-01-14
 */

@Entity  //Hibernate: Class to database table (generates SQL)
/**
 * @Table: Names database table in snake case as weight_entries
 * @Index: Production Index Composite B-Tree sorts weight by user and then by datetime
 */
@Table(name = "weight_entries", indexes = {
        @Index(name = "idx_user_date", columnList = "user_id, date_recorded"),
        @Index(name = "idx_user_updated", columnList = "user_id, updated_at")
})
@Data //Lombok: Generates Getters, Setters, toString(), equals(), and hashCode()
@NoArgsConstructor //Lombok: Generates blank constructor public User() {}
@AllArgsConstructor //Lombok: Generates constructor with all arguments passed
public class WeightEntry implements Persistable<UUID> {

    /**
     * Primary Key of table weight_entries
     * id generated via UUID
     * id : private Long wrapper allows field to be null before insertion
     */
    @Id
    private UUID id;

    /**
     * Join on "users" table on attribute users._id
     * user_id as the foreign key
     * user_id cannot be null
     * Relationship is Many to One (Many weight, One user)
     */
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Defines weight for entry
     * nullable = false: Weight entry needs a weight
     * Weight cannot be less than 1 and guard rails are placed digits for weights
     */
    @Column(nullable = false)
    @Min(value = 1, message = "Weight must be greater than 0")
    private Double weight;

    /* Defines the date and time for weight entry
     * nullable = false: Every weight entry must have a date and time associated
     */
    @Column(name = "date_recorded", nullable = false)
    @PastOrPresent(message = "Date cannot be in the future")
    private LocalDateTime date;

    //---- Sync & Audit ----

    /**
     * Audit Field: Creation Time
     * Annotation: @CreationTimestamp triggers Hibernate to set this to the current VM time
     * instantly when the entity is first INSERTED.
     * Purpose:    This is the "Server Time" validation.
     * Note: This differs from 'date' (measuredAt).
     * Example: A user weighs themselves at 8:00 AM (measuredAt), but is offline.
     * They sync at 5:00 PM. 'createdAt' will be 5:00 PM.
     */
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    /**
     * Sync Engine: The Delta Tracker
     * Annotation: @UpdateTimestamp triggers Hibernate to update this field EVERY time
     * the row is modified (Update or Soft Delete).
     * When Android asks: "Give me updates since 2026-01-14 09:00",
     * the Query checks: WHERE last_modified > '2026-01-14 09:00'.
     * This ensures the client only downloads what changed, not the whole database.
     */
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    /**
     * Sync Engine: The Tombstone
     * Purpose:    This implements "Soft Deletes."
     * Problem:    If we physically DELETE a row, the offline client won't know it's gone.
     * Solution:   We set isDeleted = true. The row stays in the DB.
     * The Sync logic sees this "Update" and tells the Android app:
     * "Hey, delete Record #123 from your local SQLite."
     */
    @Column(nullable = false)
    private Boolean isDeleted = false;

    //---UUID Persistable Implementation ---

    @Transient // Ignore this field for database columns
    private boolean isNew = true;

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew; // Tells Hibernate: "If I say it's new, Insert it."
    }

    // Call this after loading from DB so Hibernate knows it's not new anymore
    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    @Version
    @Column(columnDefinition = "integer default 0")
    private Integer version = 0;
}

