---
title: Repository Layout
sidebar_position: 2
---

# Repository Layout

```
.
├── backend/                  # Spring Boot REST service (Java 25, Maven)
│   ├── src/main/java/org/rdfarchitect/
│   │   ├── api/              # REST layer: controllers + DTOs
│   │   ├── services/         # Use cases and service implementations
│   │   ├── database/         # In-memory data store + Fuseki snapshot support
│   │   ├── rdf/              # RDF/Jena helpers (graph wrappers, formatting, merging)
│   │   ├── shacl/            # SHACL generation engine
│   │   ├── dl/               # Diagram-layout persistence (positions, sizes)
│   │   ├── models/           # Domain models (CIM, changelog, search)
│   │   ├── exception/        # Application exceptions and Spring handlers
│   │   ├── config/           # Spring configuration beans
│   │   ├── filters/          # Servlet filters (CORS, session)
│   │   ├── listeners/        # Spring event listeners
│   │   └── Launcher.java     # Spring Boot entry point
│   └── src/main/resources/
│       ├── application*.yml  # Configuration
│       └── sparql-templates/ # Parameterised SPARQL used by services
│
├── frontend/                 # SvelteKit single-page app (Svelte 5 runes)
│   └── src/
│       ├── routes/           # SvelteKit pages (homepage, /mainpage, /changelog, /compare, /migrate, /shacl)
│       └── lib/
│           ├── api/          # BackendConnection + small fetch helpers
│           ├── components/   # Reusable UI primitives + bits-ui wrappers
│           ├── models/       # DTO types and reactive wrappers ($state-backed)
│           ├── rendering/    # SvelteFlow + Mermaid diagram renderers
│           ├── dialog/       # Generic dialog scaffolding
│           ├── ttl/          # CodeMirror-based TTL editor with validation
│           ├── rdf-syntax-grammar/  # IRI/prefix/NCName validation rules
│           └── sharedState.svelte.js # Cross-component reactive state
│
├── docker/                   # Compose-based local stack (gateway + frontend + backend)
├── docs/                     # User-facing documentation (this site)
├── .github/                  # CI workflows, issue/PR templates, governance
└── CHANGELOG.md              # Manually maintained, Keep-a-Changelog style
```

The split between backend and frontend is **strict**: nothing under `backend/` imports from `frontend/`, and vice versa. The contract between them is the REST API, documented in Swagger UI at runtime.
