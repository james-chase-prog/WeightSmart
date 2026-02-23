---
layout: default
title: "Artifact 1: Software Engineering & Design"
---

<div align="center">

# Artifact 1: Software Engineering & Design

**The Architectural Foundation**

[![Java](https://img.shields.io/badge/Java_21-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](#)
[![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](#)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-6DB33F?style=flat-square&logo=spring-boot&logoColor=white)](#)
[![Android](https://img.shields.io/badge/Android-3DDC84?style=flat-square&logo=android&logoColor=white)](#)
[![Hilt](https://img.shields.io/badge/Hilt_DI-00696E?style=flat-square)](#)
[![Retrofit](https://img.shields.io/badge/Retrofit-4E5F7D?style=flat-square)](#)

</div>

---

<div class="page-nav">
<a href="./">&larr; Self-Assessment</a>
<a href="milestone2">Artifact 2: Algorithms &rarr;</a>
</div>

## What Existed Before

The original CS-360 artifact was a single-Activity Android application where business logic, data storage, and authentication all lived in the UI layer. There was no separation of concerns, no network connectivity, no external server, and no dependency injection -- manual object creation was scattered throughout Activity classes. Security consisted of basic password hashing with no token-based authentication.

## The Enhancement Goal

Transform the monolith into a **distributed full-stack ecosystem** -- a Kotlin Android client communicating with a Java Spring Boot server via REST -- to demonstrate architectural decoupling, modern design patterns, secure coding, and the engineering judgment to manage complexity across 80+ source files.

---

## Why This Artifact

This artifact was selected because it offered the perfect canvas to demonstrate everything learned within the Computer Science program. My main goal with the capstone was to prove capability as a **Full-Stack Engineer** by learning and implementing systems I had never built before. The refactoring required:

- **Architectural decoupling**: Moving business logic from the Android client to a remote Spring server, creating a distributed system where the client handles presentation (MVVM) and the server handles logic and persistence
- **Testable, modular code**: Replacing manual object creation with Hilt/Dagger dependency injection
- **Secure coding across a custom stack**: Taking what I learned during the Full Stack course (CS-310), where JWT authentication was implemented on a MEAN stack, and implementing it across a PostgreSQL-Spring-Android stack
- **Scope management**: Deliberately reducing scope from the original blueprint (which included an Angular micro-frontend and HAPI FHIR integration) to prioritize **depth over breadth**

---

## The Transformation

<table>
<tr>
<td width="50%">

<strong>Before: CS-360 Monolith</strong>
<br><br>
<ul>
<li>Single-Activity architecture</li>
<li>Business logic embedded in UI layer</li>
<li>Local-only Room storage</li>
<li>No network connectivity</li>
<li>Basic password hashing</li>
<li>No separation of concerns</li>
</ul>

</td>
<td width="50%">

<strong>After: Distributed Full-Stack Ecosystem</strong>
<br><br>
<ul>
<li>MVVM across all 5 screens</li>
<li>Spring Boot REST server (14 endpoints)</li>
<li>Offline-first sync with WorkManager</li>
<li>JWT authentication with refresh tokens</li>
<li>Hilt DI with 4 injection modules</li>
<li>Encrypted local storage (Tink AES-256-GCM)</li>
</ul>

</td>
</tr>
</table>

### System Architecture

```
┌─────────────────────────────┐    REST / JSON     ┌──────────────────────────────┐
│      Android Client         │ ◄────────────────► │     Spring Boot Server        │
│      44 Kotlin Files        │                     │     28 Java Files             │
│                             │                     │                               │
│  ┌────────────────────────┐ │                     │  ┌──────────────────────────┐ │
│  │  UI Layer              │ │                     │  │  Controller Layer        │ │
│  │  5 Fragments/Activities│ │                     │  │  AuthController (8 eps)  │ │
│  │  5 ViewModels          │ │                     │  │  WeightController (6 eps)│ │
│  ├────────────────────────┤ │                     │  ├──────────────────────────┤ │
│  │  Domain Layer          │ │                     │  │  Service Layer           │ │
│  │  5 Use Cases           │ │                     │  │  UserService             │ │
│  │  1 Repository Interface│ │                     │  │  WeightService           │ │
│  ├────────────────────────┤ │                     │  │  TrieService             │ │
│  │  Data Layer            │ │                     │  │  RateLimiterService      │ │
│  │  SessionManager (Tink) │ │                     │  ├──────────────────────────┤ │
│  │  AuthRepositoryImpl    │ │                     │  │  Security Layer          │ │
│  │  WeightRepository      │ │                     │  │  JWT Filter Chain        │ │
│  │  Room (SQLite Cache)   │ │                     │  │  Token Blacklist         │ │
│  │  Retrofit API Client   │ │                     │  │  Rate Limiter            │ │
│  ├────────────────────────┤ │                     │  ├──────────────────────────┤ │
│  │  Platform Layer        │ │                     │  │  Persistence Layer       │ │
│  │  SyncWorker            │ │                     │  │  JPA/Hibernate ORM       │ │
│  │  SyncManager           │ │                     │  │  PostgreSQL              │ │
│  │  ConnectivityObserver  │ │                     │  └──────────────────────────┘ │
│  └────────────────────────┘ │                     │                               │
└─────────────────────────────┘                     └──────────────────────────────┘
```

---

## How This Enhancement Demonstrates Course Outcomes

### Innovative Techniques & Tools

The heart of this enhancement is the adoption of **well-founded and innovative techniques** to deliver a professional-quality system. Every architectural decision was deliberate:

**MVVM + StateFlow + Channel** was applied consistently across all 5 screens. Each screen follows an identical pattern: the Fragment/Activity is a thin view layer that observes a ViewModel's `StateFlow<UiState>` for data and `Channel<Event>` for one-shot navigation or error events. This consistency is intentional -- it means any developer familiar with one screen can immediately navigate any other, reducing onboarding friction and demonstrating mastery of modern Android architecture.

**Hilt Dependency Injection** replaced manual object creation with four `@SingletonComponent`-scoped modules (App, Network, Database, Repository). All five ViewModels use `@HiltViewModel` with `@Inject constructor`, enabling compile-time dependency verification. This isn't just cleaner code -- it demonstrates understanding that testable, modular architecture is the foundation of maintainable software.

**Offline-First Synchronization** via WorkManager was the most complex engineering challenge. The system implements a 3-phase push/pull/cleanup protocol with server-wins conflict resolution, connectivity monitoring via `callbackFlow`, and periodic background sync. The details of this protocol are covered in [Artifact 3: Databases](milestone3), but from a software engineering perspective, the key insight was designing a system where the user never has to think about network state -- data is always available locally, and synchronization happens transparently.

**Retrofit + Coroutines** bridges the client and server via 10 suspend functions mapping to REST endpoints. All network operations execute off the Main Thread, preventing UI freezes. The Repository pattern abstracts whether data comes from Room (local) or the server (remote), with `WeightRepository` implementing the offline-first write strategy: local insert first, then server attempt, with SyncWorker retry on failure.

### Security Mindset

Security was implemented as a **cross-cutting concern**, not a bolted-on feature. The original artifact employed basic password hashing and input sanitization. I wanted to demonstrate secure coding throughout the entire project -- applying what I learned about JWT in the Full Stack course to a completely different technology stack.

**Server-side**: JWT access tokens (24h dev / 1h prod) with 7-day refresh tokens, a security filter chain with STATELESS sessions and explicit endpoint permissions, a token blacklist via `ConcurrentHashMap.newKeySet()` with hourly cleanup, error sanitization preventing username enumeration ("Invalid username or password" rather than "Username not found"), secrets externalization via Spring Profiles (dev hardcoded, prod uses environment variables), CORS with explicit origin allowlists, and conditional HTTPS enforcement.

**Client-side**: Session storage migrated from `EncryptedSharedPreferences` (deprecated) to Jetpack DataStore + Google Tink AES-256-GCM encryption. Sensitive fields (auth token, refresh token, email) are encrypted; `CharArray` passwords are zeroed after use (`password.fill('\u0000')`). `isTokenExpired()` checks JWT `exp` claims locally before making network calls.

This security work demonstrates the ability to **anticipate adversarial exploits** and build defenses at every layer -- from transport (HTTPS/CORS) through authentication (JWT lifecycle) to storage (Tink encryption).

### Designing & Evaluating Computing Solutions

Architectural decisions required evaluating trade-offs throughout the project. Examples:

- Choosing a **hash index on usernames** for O(1) authentication lookups versus **B-tree on emails** for less frequent registration searches -- matching the index strategy to the access pattern rather than applying one approach uniformly
- Choosing **server-wins conflict resolution** for sync rather than last-write-wins or manual merge -- recognizing that for a single-user health app, the server's version is always the most authoritative
- Choosing **manual SyncWorkerFactory** over `@HiltWorker` due to kapt/Kotlin 2.x metadata incompatibility -- demonstrating the pragmatism to find workarounds when ideal approaches are blocked by toolchain limitations
- Deliberately **reducing scope** from the Angular + HAPI FHIR blueprint to prioritize depth -- the engineering judgment to know when depth of implementation outweighs breadth of scope

### Collaborative Environments

The codebase follows a consistent documentation strategy designed to enable any developer to understand the system without tribal knowledge:

- **72 source files** (44 Kotlin, 28 Java) with standardized headers including class purpose, Architecture Role section, and key concept links to official documentation
- **KDoc/Javadoc** on all public methods with `@param`, `@return`, and data flow descriptions
- **Version tracking** via `@version` with sprint tags (e.g., `2.1 (P5: DataStore migration)`) and `@since` dates
- **Inline rationale** annotating business logic decisions at the point of implementation

This documentation standardization directly addresses the need to **employ strategies for building collaborative environments** -- the consistent Architecture Role sections in file headers provide system-level context that single-file documentation typically lacks.

### Professional Communications

This portfolio itself -- the self-assessment, the three enhancement narratives, and the ePortfolio website -- demonstrates the ability to **communicate technical complexity to diverse audiences**. The system architecture diagram above, the request lifecycle diagrams in controller headers, and the milestone narratives are designed to be readable by both technical reviewers and non-technical stakeholders.

The decision to maintain a living priority matrix (P0-P8) throughout development functioned as a product roadmap -- making deliberate, transparent decisions about what to build, what to defer, and what to sunset, with clear rationale for each.

---

## Reflection

The process of enhancing WeightSmart was a lesson in managing complexity. The biggest challenge was the offline-first synchronization -- designing a system where data flows bidirectionally between a local SQLite cache and a remote PostgreSQL database with conflict resolution, tombstone tracking, and retry logic.

The scope was deliberately reduced from the original blueprint to prioritize **depth over breadth**. The implemented Android + Spring Boot stack demonstrates the full range of planned skills -- architecture, mobile design, full-stack development, secure coding, system integration -- without the dilution of adding a third platform. The unified REST API contract was designed to support future Angular consumption without modification.

> This artifact demonstrates an ability to evolve a simple, disconnected mobile application into a sophisticated distributed system -- and more importantly, the engineering judgment to know when depth of implementation outweighs breadth of scope.

---

<div class="page-nav">
<a href="./">&larr; Self-Assessment</a>
<a href="milestone2">Artifact 2: Algorithms &rarr;</a>
</div>
