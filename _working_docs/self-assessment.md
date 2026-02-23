---
layout: default
title: Professional Self-Assessment
---

<div align="center">

# Professional Self-Assessment

**James Chase** | CS-499 Computer Science Capstone | SNHU
*Full-Stack Software Engineer | Healthcare Technology*

---

</div>

## Executive Summary

Completing the Capstone course at SNHU has been a highly rewarding and challenging experience. Within the development of the software enhancements showcased in this portfolio, I was compelled to draw from skills developed across multiple courses and, more importantly, to push myself further as a Software Engineer to deliver on architecting a distributed full-stack ecosystem.

My time at SNHU began in January 2024 as a complete novice to coding, software development, and data analytics. Balancing full-time work in healthcare while maintaining a high standard for coursework has been one of the most rewarding challenges of my life. When I reached the Capstone and discovered the opportunity to take previous course artifacts and transform them with everything I had learned, I seized the challenge to tackle a project of significant scope and complexity.

> The enhancements designed here are integrated directly with my professional identity, which is rooted at the intersection of **healthcare**, **technology**, and **data analytics**. Coming from a background in healthcare, I am driven to design systems that improve patient and provider experiences. My core values center on **security-first design**, **architectural craftsmanship**, and **meaningful data representation** to drive improved outcomes.

---

## A Growth Mindset

<table>
<tr>
<td width="50%">

### Where I Started

The very first class I took at SNHU was **IT-140: Introduction to Scripting**. I learned the foundations of Python and how to work with an IDE. The final project was a simple text-based adventure game: **one file, 293 lines**, no classes, no memory management, no complex data structures -- just unformatted print-to-console statements.

*That was the beginning.*

</td>
<td width="50%">

### Where I Am Now

The capstone project is a **distributed full-stack ecosystem**: a Spring Boot server with JWT authentication, a Kotlin Android client with MVVM architecture, offline-first synchronization, encrypted local storage, and adaptive server-side downsampling algorithms -- spanning **80+ files** across two codebases.

*The distance between these two points is the value of this program.*

</td>
</tr>
</table>

Critical software engineering principles -- object-oriented programming, UI/UX design, DRY, and Separation of Concerns -- were tackled progressively across the curriculum. **CS-305 (Software Security)** introduced me to dependency checks, Spring servers, and Spring Security, which proved foundational to this capstone. Courses covering the Software Development Lifecycle introduced Waterfall, Agile, UML diagrams, and sequence diagrams -- essential coordination tools for complex projects across diverse teams.

Later courses in Client/Server architecture, Android development, and Full-Stack Development (where a static HTML page was transformed into a dynamic MEAN stack web application) provided both the motivation and the technical skills required to transition the Android application from **CS-360 (Mobile Architecture & Programming)** into a full-stack distributed system.

---

## Capstone: The WeightSmart Transformation

The artifacts that follow are the result of comprehensive enhancements made to my CS-360 final project, **WeightSmart**, a weight tracking application. Originally developed six to nine months prior, the initial artifact was a monolith architecture where all logic, data storage, and authentication occurred directly within the Android UI layer using basic local storage.

These enhancements demonstrate my ability to evolve a simple, disconnected mobile application into a sophisticated, distributed system across **three pillars of computer science**:

<table>
<tr>
<td align="center" width="33%">

### Software Engineering & Design

Refactored the original monolith into a distributed full-stack ecosystem using **MVVM**, decoupling business logic from the Android client to a remote **Spring Boot** server with **Hilt** dependency injection and **Retrofit** REST communication.

</td>
<td align="center" width="33%">

### Algorithms & Data Structures

Replaced inefficient *O(n)* linear scans with a custom **Prefix Tree (Trie)** for *O(L)* username search, protected by a **Token Bucket** rate limiter. Implemented adaptive **downsampling algorithms** for graph data visualization.

</td>
<td align="center" width="33%">

### Databases

Migrated from local-only Room storage to a distributed relational model using **PostgreSQL** with **B-tree** indexed range scans and a bi-directional, **offline-first synchronization** strategy between SQLite cache and server.

</td>
</tr>
</table>

> Together, these artifacts illustrate my capacity to design and deliver professional-grade software solutions that are **scalable**, **efficient**, and **secure**.

---

## Course Competencies

### Collaborating in a Team Environment

I view software development as a team sport, where the quality of collaboration is as important as the quality of the code. Although this portfolio represents individual work, my approach is deliberately designed for real-world team contexts:

| Practice | Implementation |
|:---------|:---------------|
| **Consistent Architecture** | Every screen follows the same MVVM + StateFlow + Channel pattern, reducing onboarding friction for new contributors |
| **Self-Documenting Code** | KDoc (Kotlin) and Javadoc (Java) conventions applied across all 80+ files with educational-style commentary |
| **Version Control Discipline** | Meaningful Git commits, clean branching, and deliberate commit messages that communicate intent |
| **Living Documentation** | Maintained a priority matrix (P0--P8) as a product roadmap, enabling transparent scope decisions |

---

### Communicating with Stakeholders

Regardless of the quality of your work, if you are unable to explain it to both technical and non-technical audiences, the work loses most of its value. Effective engineering requires the ability to translate technical complexity into clear business value.

I have developed the skill to present architectural plans to diverse stakeholders, ensuring alignment on goals and constraints. This involves maintaining living documentation and priority matrices that function as product roadmaps -- making deliberate decisions about what to build, what to defer, and what to sunset, with clear rationale for each.

---

### Data Structures and Algorithms

Selecting data structures and algorithms is as much about understanding user and business needs as it is about programming. The correct choice is a game of trade-offs between memory and efficiency; understanding how a structure is used *and* how it is scanned is essential for scalable code.

| Structure / Algorithm | Problem Solved | Complexity |
|:----------------------|:---------------|:-----------|
| **Prefix Tree (Trie)** | Real-time username search during registration | *O(L)* lookup, where L = key length |
| **Token Bucket** | Endpoint-scoped rate limiting (auth: 5/min, search: 10/min) | *O(1)* per request check |
| **Adaptive Downsampling** | Reducing 1000+ weight records to weekly/monthly averages for graph rendering | Linear scan with grouped aggregation |
| **Sliding Window Moving Average** | Trend line smoothing with resolution-adaptive window sizes | *O(n)* single pass |
| **ConcurrentHashMap** | Thread-safe JWT token blacklist with scheduled cleanup | *O(1)* lookup and insert |

I specialize in identifying inefficiencies and replacing them with optimized structures that maximize performance through an understanding of Big-O notation.

---

### Software Engineering and Databases

I have developed the use of well-founded and innovative techniques, skills, and tools to implement computer solutions that deliver industry-specific value. My expertise includes transitioning legacy monoliths into distributed N-tier architectures that utilize modern design patterns:

```
┌─────────────────────┐     REST / JSON      ┌──────────────────────┐
│   Android Client    │ ◄──────────────────► │   Spring Boot Server  │
│                     │                       │                       │
│  ┌───────────────┐  │                       │  ┌─────────────────┐  │
│  │  UI (Fragment) │  │                       │  │   Controller    │  │
│  │  ViewModel     │  │                       │  │   Service       │  │
│  │  Use Cases     │  │                       │  │   Repository    │  │
│  │  Repository    │  │                       │  └────────┬────────┘  │
│  │  Room (SQLite) │  │                       │           │           │
│  └───────────────┘  │                       │    ┌──────▼──────┐    │
│                     │                       │    │  PostgreSQL  │    │
│  DataStore + Tink   │                       │    │  (Primary)   │    │
│  (Encrypted Cache)  │                       │    └─────────────┘    │
└─────────────────────┘                       └──────────────────────┘
```

In the database layer, I match specific indexing strategies -- B-tree for chronological range scans, composite indexes for delta sync performance -- to their ideal use cases, and enforce data integrity through optimistic locking and soft-delete tombstone patterns.

---

### Security

By focusing on security throughout the SDLC, I ensure that the code I produce is built with a security-first mindset. My approach involves wrapping computational logic in protective layers:

| Layer | Implementation |
|:------|:---------------|
| **Authentication** | JWT access tokens (1hr prod / 24hr dev) + 7-day refresh tokens with token blacklist on logout |
| **Encryption at Rest** | Jetpack DataStore + Google Tink AES-256-GCM for sensitive client-side session data |
| **Rate Limiting** | Token Bucket algorithm scoped per endpoint to defend against enumeration and brute-force attacks |
| **Secrets Management** | Spring Profiles separating dev (hardcoded) from prod (environment variables) |
| **Error Sanitization** | Generic auth error messages ("Invalid username or password") prevent information leakage |
| **Transport Security** | Conditional HTTPS enforcement in production, CORS restricted to allowed origins |
| **Input Validation** | X-Forwarded-For regex validation, DTO constraint annotations, server-side authority |

Focusing on anticipating adversarial exploits during development allows me to mitigate potential vulnerabilities and ensure the privacy of resources.

---

## Artifact Overview

The three artifacts that follow each target a distinct pillar of computer science, but they are not isolated exhibits -- they are **interdependent enhancements to a single, cohesive application**. Each artifact builds upon the others to produce a system greater than the sum of its parts:

<table>
<tr>
<td align="center" width="33%">

**[Artifact 1: Software Engineering & Design &rarr;](milestone1.md)**

*The architectural foundation*

MVVM refactoring, Hilt DI, offline-first sync pipeline, clean separation of concerns across every screen

</td>
<td align="center" width="33%">

**[Artifact 2: Algorithms & Data Structures &rarr;](milestone2.md)**

*The intelligence layer*

Trie search, Token Bucket rate limiting, adaptive downsampling, moving average computation

</td>
<td align="center" width="33%">

**[Artifact 3: Databases &rarr;](milestone3.md)**

*The persistence strategy*

Room + PostgreSQL dual-layer, delta sync, schema optimization, encrypted local storage, B-tree indexing

</td>
</tr>
</table>

> I invite you to explore each artifact with this holistic perspective in mind -- depth in software engineering, algorithmic thinking, and database design are not isolated skills. They are interdependent concerns that compound into a robust, production-quality system.

---

<div align="center">

*Built with purpose. Engineered with care.*

</div>
