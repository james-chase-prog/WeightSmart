---
layout: default
title: WeightSmart — Professional Self-Assessment
---

<div align="center" markdown="1">

# WeightSmart

**A Full-Stack Health Analytics Platform**

[![Java](https://img.shields.io/badge/Java_21-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](#)
[![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](#)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-6DB33F?style=flat-square&logo=spring-boot&logoColor=white)](#)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?style=flat-square&logo=postgresql&logoColor=white)](#)
[![Android](https://img.shields.io/badge/Android-3DDC84?style=flat-square&logo=android&logoColor=white)](#)
[![Material 3](https://img.shields.io/badge/Material_3-00696E?style=flat-square&logo=material-design&logoColor=white)](#)

*CS-499 Computer Science Capstone | SNHU*

[View Source Code](https://github.com/james-chase-prog/WeightSmart)

</div>

---

## Professional Self-Assessment

### Executive Summary

Completing the Capstone course at SNHU has been a highly rewarding and challenging experience. Within the development of the software enhancements showcased in this portfolio, I was compelled to draw from skills developed across multiple courses and, more importantly, to push myself further as a Software Engineer to deliver on architecting a distributed full-stack ecosystem.

My time at SNHU began in January 2024 as a complete novice to coding, software development, and data analytics. Balancing full-time work in healthcare while maintaining a high standard for coursework has been one of the most rewarding challenges of my life. When I reached the Capstone and discovered the opportunity to take previous course artifacts and transform them with everything I had learned, I seized the challenge to tackle a project of significant scope and complexity.

> The enhancements designed here are integrated directly with my professional identity, which is rooted at the intersection of **healthcare**, **technology**, and **data analytics**. Coming from a background in healthcare, I am driven to design systems that improve patient and provider experiences. My core values center on **security-first design**, **architectural craftsmanship**, and **meaningful data representation** to drive improved outcomes.

---

### A Growth Mindset

<table>
<tr>
<td width="50%">

<strong>Where I Started</strong>
<br><br>
The very first class I took at SNHU was <strong>IT-140: Introduction to Scripting</strong>. I learned the foundations of Python and how to work with an IDE. The final project was a simple text-based adventure game: <strong>one file, 293 lines</strong>, no classes, no memory management, no complex data structures, and a number of unformatted print-to-console statements.
<br><br>
<em>That was the beginning.</em>

</td>
<td width="50%">

<strong>Where I Am Now</strong>
<br><br>
The capstone project is a <strong>distributed full-stack ecosystem</strong>: a Spring Boot server with JWT authentication, a Kotlin Android client with MVVM architecture, offline-first synchronization, encrypted local storage, and adaptive server-side downsampling algorithms.  This comprehensive projects spans <strong>80+ files</strong> across two codebases.
<br><br>
<em>The distance between these two points is the value of this program.</em>

</td>
</tr>
</table>

Critical software engineering principles including object-oriented programming, UI/UX design, DRY, and Separation of Concerns were tackled progressively across the curriculum. **CS-305 (Software Security)** introduced me to dependency checks, Spring servers, and Spring Security, which proved foundational to this capstone. Courses covering the Software Development Lifecycle introduced Waterfall, Agile, UML diagrams, and sequence diagrams all required for coordination of complex projects across diverse teams; and the latter three of which were implemented in the management of this project.

Later courses in Client/Server architecture, Android development, and Full-Stack Development (where a static HTML page was transformed into a dynamic MEAN stack web application) provided both the motivation and the technical skills required to transition the Android application from **CS-360 (Mobile Architecture & Programming)** into a custom full-stack distributed system.

---

### Capstone: The WeightSmart Transformation

The artifacts that follow are the result of comprehensive enhancements made to my CS-360 final project, **WeightSmart**, a weight tracking application. Originally developed six to nine months prior, the initial artifact was a monolith architecture where all logic, data storage, and authentication occurred directly within the Android UI layer using basic local storage.

These enhancements demonstrate my ability to evolve a simple, disconnected mobile application into a sophisticated, distributed system across **three pillars of computer science**:

<table>
<tr>
<td align="center" width="33%">

<strong>Software Engineering & Design</strong>
<br><br>
Refactored the original monolith into a distributed full-stack ecosystem using <strong>MVVM</strong>, decoupling business logic from the Android client to a remote <strong>Spring Boot</strong> server with <strong>Hilt</strong> dependency injection and <strong>Retrofit</strong> REST communication.

</td>
<td align="center" width="33%">

<strong>Algorithms & Data Structures</strong>
<br><br>
Replaced inefficient <em>O(n)</em> linear scans with a custom <strong>Prefix Tree (Trie)</strong> for <em>O(L)</em> username search, protected by a <strong>Token Bucket</strong> rate limiter. Implemented adaptive <strong>downsampling algorithms</strong> for graph data visualization.

</td>
<td align="center" width="33%">

<strong>Databases</strong>
<br><br>
Migrated from local-only Room storage to a distributed relational model using <strong>PostgreSQL</strong> with <strong>B-tree</strong> indexed range scans and a bi-directional, <strong>offline-first synchronization</strong> strategy between SQLite cache and server.

</td>
</tr>
</table>

> Together, these artifacts demonstrate my capacity to design and deliver professional-grade software solutions that are **scalable**, **efficient**, and **secure**.

---

## Course Competencies

### Collaborating in a Team Environment

I view software development as a team sport, where the quality of collaboration is as important as the quality of the code. Although this portfolio represents individual work, my approach is deliberately designed for real-world team contexts:

| Practice | Implementation |
|:---------|:---------------|
| **Consistent Architecture** | Every screen follows the same MVVM + StateFlow + Channel pattern, reducing onboarding friction for new contributors |
| **Self-Documenting Code** | KDoc (Kotlin) and Javadoc (Java) conventions applied across all 80+ files with educational-style commentary |
| **Version Control Discipline** | Meaningful Git commits, clean branching, and deliberate commit messages that communicate intent |
| **Living Documentation** | Maintained a priority matrix (P0–P8) as a product roadmap, enabling transparent scope decisions |

---

### Communicating with Stakeholders

Regardless of the quality of your work, if you are unable to explain it to both technical and non-technical audiences, the work loses most of its value. Effective engineering requires the ability to translate technical complexity into clear business value.

I have developed the skill to present architectural plans to diverse stakeholders, ensuring alignment on goals and constraints. This involves maintaining living documentation and priority matrices that function as product roadmaps — making deliberate decisions about what to build, what to defer, and what to sunset, with clear rationale for each.

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

In the database layer, I match specific indexing strategies such as composite B-tree for chronological range scans and composite indexes for delta sync performance to their ideal use cases, and enforce data integrity through optimistic locking and soft-delete tombstone patterns.

---

### Security

By focusing on security throughout the SDLC, I ensure that the code I produce is built with a security-first mindset. My approach involves wrapping computational logic in protective layers:

| Layer | Implementation |
|:------|:---------------|
| **Authentication** | JWT access tokens (1hr prod / 24hr dev) + 7-day refresh tokens with token blacklist on logout |
| **Encryption at Rest** | Jetpack DataStore + Google Tink AES-256-GCM for sensitive client-side session data |
| **Rate Limiting** | Token Bucket algorithm scoped per endpoint to defend against enumeration and brute-force attacks |
| **Secrets Management** | Spring Profiles separating dev (hardcoded) from prod (environment variables) |
| **Error Sanitization** | Generic auth error messages prevent information leakage during authentication flows |
| **Transport Security** | Conditional HTTPS enforcement in production, CORS restricted to allowed origins |
| **Input Validation** | X-Forwarded-For regex validation, DTO constraint annotations, server-side authority |

Focusing on anticipating adversarial exploits during development allows me to mitigate potential vulnerabilities and ensure the privacy of resources.

---

## Code Review: The Original Artifact

Before diving into the enhanced artifacts, the following video series presents a structured code review of the **original CS-360 monolith** — the application as it existed before any capstone enhancements. These reviews establish the baseline that motivated every architectural decision documented in the enhancement narratives that follow. The original source code is available [here](https://github.com/james-chase-prog/WeightSmart/tree/main/WeightSmartApp).

**Video 1: Architectural Integrity & MVVM Implementation** — This review examines the original application's package structure, revealing that while Clean Architecture layers were present (core, data, domain, UI), the MVVM pattern was incomplete. ViewModels existed as empty stubs while Fragments directly injected and called UseCases resulting in bypassing the undeveloped ViewModel layer entirely. The review identifies dead code, monolithic leftovers such as unused injections and stub implementations, and assesses the application's readiness for client-server communication. Within this assessment I assess the complete absence of networking libraries and API interfaces.

<div align="center"><em>[Full Code Review](https://youtu.be/kxVFK3ui6yA)</em></div>

**Video 2: Defensive Programming & Security Audit** — This review audits input validation across registration fields (username bounds, password length, email regex, weight range), identifies gaps such as missing bounds checks in the Weight factory method and absent network timeout configuration, and evaluates floating-point handling. The review highlights both strengths such as BigDecimal rounding, zero-division guards, epsilon comparisons for floating-point equality as well as areas for improvement, including resource management patterns and the need for defensive null-safety when transitioning from local Room queries to server JSON responses. Here I also discuss how transitioning to a distributed architecture changes the security needs and the decision to implement JWT authentication. 

<div align="center"><em>VIDEO_PLACEHOLDER_2</em></div>

**Video 3: Code Quality, Maintainability & Documentation** — This review evaluates the codebase's long-term maintainability by examining variable naming conventions, type safety opportunities (String fields that should be enums), and documentation coverage gaps across UseCases and Repositories. It identifies modules carrying too many responsibilities including the TableFragment handling pagination, sorting, filtering, data fetching, and delete logic in a single class.  Here I also perform a self-audit on constants extraction, if/case branch coverage, and adherence to Android/Kotlin naming standards. Finally, this includes an assessment on commenting pratices within the artifact. 

<div align="center"><em>VIDEO_PLACEHOLDER_3</em></div>

---

## Artifact Navigation

The three artifacts below each target a distinct pillar of computer science. However, instead of completing this work on three unique artifacts, a singular artifact was chosen from which specifc portions are pulled as the source artifacts as they are **interdependent enhancements to a single, cohesive application**. Each builds upon the others to produce a system greater than the sum of its parts.

<table>
<tr>
<td align="center" width="33%">

<h3><a href="enhancement1">Software Engineering & Design</a></h3>
<em>The architectural foundation</em>
<br><br>
MVVM refactoring, Hilt DI, offline-first sync pipeline, clean separation of concerns across every screen
<br><br>
<a href="enhancement1"><strong>Explore Artifact 1 &rarr;</strong></a>

</td>
<td align="center" width="33%">

<h3><a href="enhancement2">Algorithms & Data Structures</a></h3>
<em>The intelligence layer</em>
<br><br>
Trie search, Token Bucket rate limiting, adaptive downsampling, moving average computation
<br><br>
<a href="enhancement2"><strong>Explore Artifact 2 &rarr;</strong></a>

</td>
<td align="center" width="33%">

<h3><a href="enhancement3">Databases</a></h3>
<em>The persistence strategy</em>
<br><br>
Room + PostgreSQL dual-layer, delta sync, schema optimization, encrypted local storage, B-tree indexing
<br><br>
<a href="enhancement3"><strong>Explore Artifact 3 &rarr;</strong></a>

</td>
</tr>
</table>

---

<div align="center" markdown="1">

[Back to Main Portfolio](https://james-chase-prog.github.io) | [View Source Code](https://github.com/james-chase-prog/WeightSmart)

</div>
