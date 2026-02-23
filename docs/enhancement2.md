---
layout: default
title: "Artifact 2: Algorithms & Data Structures"
---

<div align="center" markdown="1">

# Artifact 2: Algorithms & Data Structures

**The Intelligence Layer**

[![Java](https://img.shields.io/badge/Java_21-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](#)
[![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](#)
[![Trie](https://img.shields.io/badge/Prefix_Tree-00696E?style=flat-square)](#)
[![Token Bucket](https://img.shields.io/badge/Token_Bucket-4E5F7D?style=flat-square)](#)
[![DiffUtil](https://img.shields.io/badge/DiffUtil-3DDC84?style=flat-square&logo=android&logoColor=white)](#)
[![Coroutines](https://img.shields.io/badge/Coroutines-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](#)

</div>

---

<div class="page-nav">
<a href="enhancement1">&larr; Artifact 1: Software Engineering</a>
<a href="enhancement3">Artifact 3: Databases &rarr;</a>
</div>

<small><a href="https://github.com/james-chase-prog/WeightSmart/raw/main/_working_docs/CS499%20Milestone%203.docx">Download Original Submission (docx)</a></small>

## What Existed Before

The original CS-360 artifact had no attention to time complexity. Registration checked linearly through a list of registered users -- **O(N)** -- and there was no rate limiting, no debouncing, no sorted data views, and no intelligent data structures of any kind. The codebase was full of inefficient O(N) operations ignored for the sake of building a functional UI/UX.

## The Enhancement Goal

Replace linear scans with purpose-built data structures, implement a **search-as-you-go** username registration feature powered by a Prefix Tree (Trie), and protect all exposed endpoints with a Token Bucket rate limiter which demonstrates the ability to **design and evaluate computing solutions using algorithmic principles** while maintaining a **security-first mindset**.

---

## Why This Artifact

This artifact was selected because the inefficiencies in the original code provided ample room to demonstrate mastery of data structures and algorithms. The enhancement best demonstrates two course outcomes:

1. **Designing and evaluating computing solutions** -- selecting the right data structure for each problem based on trade-offs between memory and efficiency, understanding how a structure is used *and* how it is scanned
2. **Developing a security mindset** -- recognizing that every data structure has exploitable weaknesses and building protective layers around them

The Trie replaced O(N) linear scans with O(L) lookups. But Tries are inherently heap memory greedy, so I made a series of design choices to limit their weakness: sparse initialization, recursive pruning on delete, and a 3 AM daily rebuild. I also identified the data volatility of the Trie and chose to use it **only for search** -- the actual registration uniqueness check trusts the B-tree index over PostgreSQL, which natively prevents duplicate user creation. This distinction demonstrates understanding that **in-memory data structures are caches, not sources of truth**.

I quickily identified the weakness of the Trie which is that without guardrails, a malicious user could map out all usernames by scripting rapid prefix searches. This drove implementation of the Token Bucket algorithm and brute-force prevention methods. This demonstrates a **security focus at the software level**, a critical behavior for the growing field of zero trust architecture.

---

## The Prefix Tree (Trie)

### How It Works

A Trie connects child nodes representing single characters. As the user types, a path is traced from the root down node by node. When the user types a character that does not exist within the current node path, the search terminates immediately - the prefix doesn't exist.

```
           Root
          / | \
         j  b   t
         |  |   |
         a  o   e
        / \  \   \
       m   n  b   s
      / \   \  (end: "bob")  \
     e   i   e               t
     |   |   |              (end: "test")
     s   e   t
(end: "james") (end: "jamie") (end: "janet")
```

Usernames "james", "jamie", and "janet" share the "ja" prefix, requiring **2 shared nodes** instead of 6.  This was the core reason for choosing the space efficiency of sparse allocation. Only characters that exist in actual usernames create child entries (lazy initialization, matching the Module One plan).

### Complexity Improvement

| Operation | Original (CS-360) | Enhanced | Improvement |
|:----------|:-------------------|:---------|:------------|
| Username lookup | O(N) linear scan | **O(L)** Trie traversal | Constant w.r.t. dataset size |
| Username insert | O(N) duplicate check | **O(L)** Trie insert | Independent of user count |
| Username delete | N/A | **O(L)** recursive prune | With memory reclamation |

For 10,000 users and a 10-character username, the old approach required up to **10,000 comparisons**; the Trie requires exactly **10 node lookups**.

### Write-Through Cache Pattern

When a new user registers, the system updates both the persistent store and the in-memory index in a single operation — a write-through cache pattern:

```
Register User
    ├── userRepository.save(user)     ← PostgreSQL (source of truth)
    └── trieService.insert(username)  ← Trie (in-memory cache)
```

This ensures the Trie search index remains consistent with the database at all times, eliminating the window where a newly registered username would be invisible to the prefix search. This demonstrates **design computing solutions that manage data consistency across abstraction layers**.

### Memory Lifecycle Management

Tries consume heap memory proportional to the total character count of all stored usernames. Without lifecycle management, deleted usernames would leave orphaned branches consuming memory indefinitely. Two mechanisms address this:

| Mechanism | Schedule | Purpose |
|:----------|:---------|:--------|
| **Recursive Delete** | On demand | Backtracks up the call stack, pruning empty branches |
| **Daily Rebuild** | 3:00 AM cron | Atomic swap: new tree from PostgreSQL, old root replaced via `volatile` reference |

The `volatile` keyword ensures Java Memory Model happens-before visibility - any thread reading `root` after the swap sees the fully-constructed new tree. This demonstrates understanding of **concurrent data structure correctness**, not just functional correctness.

---

## The Token Bucket Rate Limiter

### Why It Exists

Without rate limiting, the Trie's prefix search endpoint becomes a username enumeration tool. An attacker could script rapid searches ("a", "b", "c", ..., "aa", "ab", ...) to map out every username in the system. The Token Bucket algorithm constrains this to bounded request rates per IP address, making full enumeration impractical -- directly addressing the outcome of **anticipating adversarial exploits**.

### Dual-Scope Design

| Scope | Capacity | Rate | Rationale |
|:------|:---------|:-----|:----------|
| **Search** (`/search-usernames`) | 10 tokens | 10/min | Lenient: type-ahead generates rapid requests |
| **Auth** (`/login`, `/register`, `/refresh`) | 5 tokens | 5/min | Strict: brute-force protection |

The original Module One plan only specified rate limiting for search. During implementation, I recognized that authentication endpoints were equally vulnerable and added a separate, stricter bucket. This demonstrates the security-first design evolution that occurs when you actively think about attack surfaces during development.

### Thread Safety & Cleanup

- **Per-bucket**: `synchronized` ensures concurrent requests from the same IP correctly decrement tokens
- **Cross-bucket**: `ConcurrentHashMap.computeIfAbsent()` provides thread-safe lazy initialization
- **Scheduled cleanup**: Every 5 minutes, buckets not accessed for 10 minutes are evicted -- preventing unbounded memory growth from one-time visitors

---

## Client-Side Search Pipeline

The server-side Trie and Token Bucket are complemented by a client-side **coroutine-based debounce** that creates a two-tier throttling system:

```
Keystroke → triggerUsernameSearch()
              │
              ├── Cancel previous Job      ← Structured concurrency
              ├── Guard: < 3 chars? → skip
              ├── Guard: > 20 chars? → skip (matches server max)
              │
              └── Launch new coroutine:
                    delay(300ms)           ← Debounce window
                    authRepository.searchUsernames(query)
                    ├── "Username taken" (red)
                    └── "Username available" (green)
```

For a user typing at 5 characters/second, the debounce reduces server calls from **5/second to ~1 every 500ms**. This layered defense demonstrates the ability to **evaluate computing solutions that manage complexity across client and server boundaries**:

| Layer | Defense |
|:------|:--------|
| Client: Length validation (3-20 chars) | Prevents unnecessary network calls |
| Client: 300ms coroutine debounce | Reduces call frequency |
| Server: Max-depth guard (20 chars) | Catches client bypass |
| Server: Token Bucket (10/min, 5/min) | Catches automated enumeration |
| Server: MAX_SEARCH_RESULTS = 10 | Bounds information leakage per query |

---

## Additional Algorithmic Implementations

Beyond the Trie and Token Bucket, this enhancement includes several additional algorithmic demonstrations:

**Multi-Column Sorting** -- The weight log table implements toggle-based sorting using Kotlin's `sortedBy`/`sortedByDescending`, which maps to **Timsort** under the JVM (O(N log N)). The `recompute()` pipeline chains filter → daypart filter → sort → paginate → format → emit state -- a functional data transformation pipeline.

**DiffUtil (Myers Diff Algorithm)** -- The `WeightEntryAdapter` uses `ListAdapter` with `DiffUtil`, calculating the **minimal set of insert/remove/move operations** to update the RecyclerView (O(N + D<sup>2</sup>) where D = edit distance), replacing the original full-rebind approach.

**Goal Direction Inference** -- Rather than requiring users to select "weight loss" or "weight gain," the algorithm infers direction from historical data: `goalIsWeightLoss = firstRecordedWeight > goalWeight`. A celebration guard stores the celebrated goal value and auto-resets when the goal changes.

**Adaptive Downsampling** -- The graph data endpoint applies resolution-adaptive downsampling: raw data for small datasets, weekly ISO-week averages when records exceed 500, monthly averages if weekly points still exceed 500. A sliding window moving average provides trend line smoothing with adaptive window sizes.

---

## How This Enhancement Demonstrates Course Outcomes

This enhancement is strongest at demonstrating mastery of **data structures and algorithms** and **security-focused development**. The Trie provides O(L) lookup where the original monolith required O(N). The Token Bucket provides O(1) rate checking. But beyond raw complexity improvements, the enhancement demonstrates:

- **Evaluating computing solutions**: The decision to trust the B-tree index for registration uniqueness (not the Trie) shows understanding of when in-memory structures are appropriate versus when persistent guarantees are required
- **Security mindset**: Every algorithm was analyzed for exploitable weaknesses - the Trie's enumeration vulnerability drove the Token Bucket, the Trie's memory greed drove sparse allocation and pruning, the search endpoint's exposure drove layered client/server defenses
- **Innovative techniques**: Coroutine-based debounce via structured concurrency, `ConcurrentHashMap` for thread-safe rate limiting, `@PostConstruct` + `@Scheduled` for Trie lifecycle management
- **Collaborative environments**: Educational-style Javadoc/KDoc across TrieService, RateLimiterService, and RegistrationViewModel with Big-O analysis and concept references
- **Professional communications**: This narrative articulates the Write-Through Cache learning moment and the Trie-to-Token-Bucket security reasoning for both technical and non-technical audiences

---

## Reflection

The process of enhancing WeightSmart's algorithmic layer was an exercise in balancing theoretical efficiency with practical application. The key learning was that **data structures have operational requirements beyond their core operations** of insertion, search, and deletion.  Memory management, consistency guarantees, and security hardening are equally important for production systems.

The implementation went beyond the original Module One blueprint in several meaningful ways: the planned boolean `/check-availability` endpoint became a richer prefix search; single-scope rate limiting expanded to dual-scope protection; and P1 memory leak remediation added lifecycle management the original plan never contemplated. Each extension was driven by real problems discovered during development. This is reflective of the kind of iterative refinement that distinguishes production engineering from academic exercises.

> This artifact demonstrates the ability to identify algorithmic inefficiencies, replace them with purpose-built data structures that maximize performance, and protect those structures from adversarial exploitation -- understanding that the correct choice is always a game of trade-offs between memory, efficiency, and security.

---

<div class="page-nav">
<a href="enhancement1">&larr; Artifact 1: Software Engineering</a>
<a href="enhancement3">Artifact 3: Databases &rarr;</a>
</div>
