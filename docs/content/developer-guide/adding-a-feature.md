---
title: Adding a Feature End-to-End
sidebar_position: 8
---

# Adding a Feature End-to-End

A checklist of the layers a typical feature touches, plus how to find the canonical pattern in each layer.

## The eight layers

| Layer | Where | What you add |
| ----- | ----- | ------------ |
| 1. Use case interface | `services/<feature>/` | Declares the operation. |
| 2. Use case implementation | same package | The actual orchestration / Jena code. |
| 3. SPARQL template | `resources/sparql-templates/` | The query, when one is needed. |
| 4. DTO | `api/dto/<feature>/` | Request / response shape. |
| 5. Mapper | `api/dto/<feature>/` | MapStruct interface, domain ↔ DTO. |
| 6. Controller | `api/controller/...` | The endpoint. |
| 7. Frontend API method | `frontend/src/lib/api/` | Call wrapper. |
| 8. Frontend usage | `frontend/src/routes/...` | UI. |

Plus tests at every layer that has logic.

## How to learn the pattern in each layer

The codebase is large enough that a printed example would go stale. Instead, before writing your feature:

1. **Find the closest existing endpoint.** For a new "list X" endpoint, locate any `*RESTController` whose verb and shape match — same HTTP method, same kind of return value (single resource / list / page).
2. **Trace it down to the database.** From that controller, follow the calls into `services/`, the SPARQL template (if any), and the DTO/mapper. That trace *is* the recipe for your feature.
3. **Copy the structure.** Don't try to "improve" it; if the existing pattern uses a record DTO, make yours a record. If it uses MapStruct, do the same. Reviewers will notice and slow you down if you diverge.

## Recommended order of work

1. Decide the contract first: write the use-case interface and the DTO. They're cheap and force clarity.
2. Implement the use case against the in-memory database adapter, with a test fixture small enough to fit on a screen.
3. Wire the controller; add a controller test that mocks the use case.
4. Add the frontend API call. URL composition goes through the existing backend wrapper; do **not** hand-roll fetch URLs.
5. Build the UI, reusing existing components, dialogs, and reactive primitives.

## Pre-PR checklist

```bash
# Backend
cd backend
mvn -B spotless:apply
mvn -B verify

# Frontend
cd ../frontend
npm run format
npm run lint
npm run test
npm run build
```

If everything passes, your PR is ready. CI reruns all of this on the cloud.

## Don'ts

- Don't put SPARQL strings inside Java code; load templates from the resources folder.
- Don't bypass the database port and reach into Jena directly from a service.
- Don't add a parallel state store on the frontend; reuse the existing primitives.
- Don't construct backend URLs in components — go through the existing API wrapper.
- Don't skip a write to the change history when your service mutates the model.
