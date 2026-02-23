---
layout: default
title: "Artifact 3: Databases"
---

<div align="center" markdown="1">

# Artifact 3: Databases

**The Persistence Strategy**

[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?style=flat-square&logo=postgresql&logoColor=white)](#)
[![Room](https://img.shields.io/badge/Room_SQLite-3DDC84?style=flat-square&logo=android&logoColor=white)](#)
[![Hibernate](https://img.shields.io/badge/Hibernate_ORM-59666C?style=flat-square&logo=hibernate&logoColor=white)](#)
[![WorkManager](https://img.shields.io/badge/WorkManager-00696E?style=flat-square)](#)
[![Tink](https://img.shields.io/badge/Tink_AES--256--GCM-4E5F7D?style=flat-square)](#)
[![JPA](https://img.shields.io/badge/Spring_Data_JPA-6DB33F?style=flat-square&logo=spring&logoColor=white)](#)

</div>

---

<div class="page-nav">
<a href="enhancement2">&larr; Artifact 2: Algorithms</a>
<a href="./">Self-Assessment &rarr;</a>
</div>

<small><a href="https://github.com/james-chase-prog/WeightSmart/raw/main/_working_docs/CS499%20Milestone%204.docx">Download Original Submission (docx)</a></small>

## What Existed Before

The original CS-360 artifact stored weight data in a local-only Room database with **no indexing, no pagination, no audit trail, no server-side database, and no synchronization** across multiple clients. There was no mechanism for data to leave the device, no concept of soft deletes, and session data was stored in basic SharedPreferences without encryption.

## The Enhancement Goal

Migrate from local-only storage to a **distributed dual-layer architecture** using PostgreSQL as the authoritative source of truth with Room as a local cache.  Additionally, the enhancemnt involved implementing proper indexing strategies, an offline-first synchronization protocol, encrypted session storage, and data integrity controls that handle the messy realities of intermittent connectivity and concurrent access.

---

## Why This Artifact

This artifact was selected because the data storage implementation reflected a minimum viable approach which acted as a skeleton through which I could demonstrate database design skills. This enhancement is strongest at demonstrating mastery of **software engineering and databases** through:

1. **Relational schema design** with proper normalization, constraints, and the architectural decision to use UUIDs for offline ID generation rather than sequential integers
2. **Indexing strategy selection** -- matching B-tree indexes to specific query patterns, understanding why a B-tree on `username` is more versatile than the originally planned Hash index
3. **Distributed systems thinking** -- the 3-phase offline-first sync protocol with tombstone-based soft deletes, delta synchronization, and server-wins conflict resolution
4. **Data integrity** -- optimistic locking, transactional atomicity, and encrypted session storage

---

## Indexing Strategy: Designing Computing Solutions

The project encompassed designing and evaluating computing solutions through indexing decisions. The username column uses a Hash index for O(1) equality lookups during authentication — login, JWT validation, and profile retrieval are all equality-only queries (WHERE username = ?), making Hash the optimal structure. Because JPA's @Index annotation only supports B-tree, this required a schema.sql file with PostgreSQL-native DDL (CREATE INDEX ... USING hash), executed after Hibernate's auto-DDL via deferred datasource initialization. Prefix search is handled entirely by the in-memory enhancement2, so the database index never needs to support range or LIKE queries. The UNIQUE constraint on username still maintains its own B-tree for duplicate prevention.


```
users table:
    idx_users_username      Hash on (username)            ← Auth lookups: O(log n)
    UNIQUE on username      Auto-generated B-tree         ← Duplicate prevention
    UNIQUE on email         Auto-generated B-tree         ← Duplicate prevention

weight_entries table:
    idx_user_date           B-tree on (user_id, date)     ← Paginated history queries
    idx_user_updated        B-tree on (user_id, updated)  ← Delta sync queries
```

The `idx_user_updated` composite index was added specifically for the delta sync endpoint -- converting `WHERE user_id = ? AND updated_at > ? ORDER BY updated_at ASC` from a sequential scan to an **index range scan**. Additionally, the delta sync query was changed from `Page<WeightEntry>` to `Slice<WeightEntry>`, eliminating an unnecessary `SELECT COUNT(*)` per page and **halving the query count** during synchronization. These are examples of matching indexing strategies and query interfaces to their specific use cases.

---

## Offline-First Synchronization: Innovative Techniques

The offline-first sync protocol is the most architecturally significant achievement. It demonstrates mastery of **well-founded and innovative techniques** such as distributed systems patterns that ensure data integrity across multiple clients with intermittent connectivity.

### The 3-Phase Protocol

```
Phase 1: PUSH                Phase 2: PULL               Phase 3: CLEANUP
─────────────────          ─────────────────           ─────────────────
Query unsynced rows  →     Initial: 100 records  →     Delete synced
from Room (isSynced        via pagination              tombstones from
= false)                   ─── OR ───                  local Room
       │                   Delta: changes since
       ▼                   last sync timestamp
POST each to server              │
       │                         ▼
       ▼                   Upsert into Room
Mark isSynced = true       (server-wins on
in Room                    ID collision)
```

**Phase 1 (PUSH)** finds all local records with `isSynced = 0` and pushes them to the server. Soft-deleted entries trigger `DELETE` requests; new entries trigger `POST` requests. Individual failures are isolated -- one failed entry doesn't block the rest.

**Phase 2 (PULL)** uses either an initial full-pull (100 records via pagination on first login) or a delta pull via `GET /sync?since={timestamp}` for subsequent syncs. Active records are UPSERTed (server-wins); tombstones (`isDeleted = true`) trigger local hard-deletes.

**Phase 3 (CLEANUP)** removes local tombstones that have been acknowledged by the server: `DELETE FROM weight_entries WHERE isDeleted = 1 AND isSynced = 1`.

### Tombstone Lifecycle

The tombstone pattern coordinates distributed deletion across clients that may be offline at different times:

```
1. User deletes on Device A  →  Local: isDeleted=1, isSynced=0
2. SyncWorker pushes         →  Server: soft-delete (updatedAt bumped)
3. Server confirms           →  Device A: hard-delete locally
4. Device B syncs later      →  Server returns tombstone in delta response
5. Device B processes        →  Hard-delete from Device B's local Room
```

### Network Resilience

The sync system provides four layers of resilience: `ConnectivityObserver` (triggers sync on network restore via `callbackFlow`), WorkManager constraints (`NetworkType.CONNECTED` + battery-aware), retry with exponential backoff (3 attempts, 30s initial), and per-entry error isolation. This ensures the user never has to think about network state -- data is always available locally, and synchronization happens transparently.

---

## Data Integrity: Security Mindset

### Optimistic Locking

Both `User` and `WeightEntry` entities carry `@Version` fields. Hibernate auto-increments the version on every UPDATE and includes `WHERE version = ?` in the SQL. If two concurrent requests modify the same row, the second receives a `StaleObjectStateException` thus preventing silent data corruption.

> **Bug Discovery**: Adding `@Version` to `User.java` caused existing database rows (with `version = NULL`) to throw `DataIntegrityViolationException` on every save. The fix required both a code change (`columnDefinition = "integer default 0"`, `version = 0`) and a database backfill. This was a valuable lesson in the gap between schema design theory and production deployment reality.

### Transactional Atomicity

Multi-step database operations use `@Transactional` to ensure atomicity: if saving a weight entry fails, the user's currentWeight cache update rolls back too. Read-only operations use `@Transactional(readOnly = true)` to enable Hibernate optimizations (no dirty checking, no flush).

### Tombstone-Aware Caching

The `updateCurrentWeightCache()` method filters out soft-deleted entries (`isDeletedFalse`). Without this, deleting the most recent weight entry would leave the dashboard displaying a "ghost" weight the user had already removed.

### Pre-Logout Data Flush

A `pushPendingChanges()` method was added as defense-in-depth: called during logout before clearing the session, it pushes all unsynced entries to the server. This closes the edge case where an immediate API call failed, SyncWorker hasn't retried yet, and the user logs out. Without the flush, those changes would be silently lost.

### Encrypted Session Storage

Session data migrated from `EncryptedSharedPreferences` (deprecated) to **Jetpack DataStore + Google Tink** (AES-256-GCM). Sensitive fields (auth token, refresh token, email) are encrypted via the `Aead` interface; non-sensitive profile data is stored in plaintext. The DTO layer strips password hashes, failed attempt counts, and lock timers from API responses, preventing data leakage. These measures demonstrate the ability to **anticipate adversarial exploits** at the data layer.

---

## Schema Design Decisions

Several schema decisions deviated from the Module One plan based on insights gained during implementation:

| Decision | Planned | Implemented | Why |
|:---------|:--------|:------------|:----|
| **Weight PK** | `SERIAL` (sequential) | `UUID` | Enables offline ID generation without server round-trip; prevents sequential enumeration |
| **Password** | `hash` + `salt` (2 fields) | Single BCrypt field | BCrypt embeds salt in hash string; Spring Security standard |
| **Username Index** | Hash O(1) | B-tree O(log n) | B-tree supports prefix queries needed for Trie search |
| **Sync fields** | *Not planned* | `createdAt`, `updatedAt`, `isDeleted`, `isSynced`, `version` | Required for offline-first sync, tombstones, and optimistic locking |

The UUID decision is particularly significant -- it demonstrates understanding that **offline-first architecture requires client-side ID generation**, and sequential IDs create both coordination problems and security vulnerabilities.

---

## How This Enhancement Demonstrates Course Outcomes

This enhancement is strongest at demonstrating mastery of **databases and software engineering**. But it touches all five outcomes:

- **Designing and evaluating computing solutions**: B-tree vs Hash index trade-offs matched to query patterns; `Page` vs `Slice` optimization eliminating unnecessary COUNT queries; UUID vs SERIAL primary keys for offline ID generation
- **Innovative techniques**: 3-phase offline-first sync protocol, `Persistable<UUID>` for client-generated IDs, WorkManager with `CoroutineWorker` for battery-aware background sync, delta sync with high-watermark timestamps, Tink AES-256-GCM encryption
- **Security mindset**: DTO layer prevents data leakage, `@PastOrPresent` prevents future-date manipulation, Spring Profiles externalize production credentials, `@Transactional` ensures atomicity, optimistic locking prevents concurrent corruption
- **Collaborative environments**: During this phase I was very aggressive towards ensuring code commenting was thorough and descriptive -- universally adapting file header comments designed to be self-documenting and educational, providing direct links to relevant documentation on the systems and libraries employed
- **Professional communications**: This narrative articulates the tombstone insight, the `@Version` bug discovery, and the indexing trade-off reasoning for both technical reviewers and non-technical stakeholders

---

## Reflection

The process of enhancing WeightSmart's database architecture reinforced several important lessons about distributed data management. The most significant was the tombstone insight which showed that physical deletes are incompatible with offline-first architecture. This single realization cascaded into the entire sync protocol design: audit timestamps, soft-delete flags, delta endpoints, and cleanup phases.

The scope significantly exceeded the original Module One plan: sync metadata fields were not anticipated, the tombstone pattern was not planned, the delta sync endpoint goes beyond the simple full-pull strategy originally described, and optimistic locking added production-quality concurrency guards.

> This artifact demonstrates the ability to evolve a single-device, unindexed local database into a distributed dual-layer architecture with proper indexing, synchronization, encryption, and data integrity controls -- engineering that tolerates the messy realities of intermittent connectivity, concurrent access, and adversarial environments.

---

<div class="page-nav">
<a href="enhancement2">&larr; Artifact 2: Algorithms</a>
<a href="./">Self-Assessment &rarr;</a>
</div>
