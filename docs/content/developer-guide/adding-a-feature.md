---
title: Adding a Feature End-to-End
sidebar_position: 5
---

# Adding a Feature End-to-End

A short worked example: suppose you want to add an endpoint that lists all *abstract* classes in a graph and surface them in the navigation tree with an italic style.

## Step 1 — Backend use case

```java
// services/select/ListAbstractClassesUseCase.java
public interface ListAbstractClassesUseCase {
    List<ClassDTO> listAbstractClasses(GraphIdentifier graphIdentifier);
}
```

## Step 2 — Service implementation

Either extend an existing service (if one already holds the same `DatabasePort` and is logically related — e.g. `QueryClassService`) or create a new `services/select/AbstractClassQueryService` that implements the use case and wires the `DatabasePort` via `@RequiredArgsConstructor`.

## Step 3 — Controller endpoint

Add a new `@GetMapping` to `AllClassesRESTController` (or a new controller if a new path tier is involved). Keep the controller thin — call the use case, log on receive and respond.

```java
@GetMapping("/abstract")
@Operation(summary = "list abstract classes", tags = {"class"})
public List<ClassDTO> listAbstract(@PathVariable String datasetName,
                                    @PathVariable String graphURI,
                                    @RequestHeader(...) String originURL) {
    logger.info("Received GET request: \".../abstract\" from \"{}\".", originURL);
    var result = listAbstractClassesUseCase.listAbstractClasses(...);
    logger.info("Sending response to GET request: \".../abstract\" to \"{}\".", originURL);
    return result;
}
```

## Step 4 — Tests

- Unit-test the service against the in-memory database (`InMemoryDatabaseImpl`).
- Integration-test the endpoint with `@SpringBootTest` + `MockMvc` if the routing or DTO shape is non-trivial.

## Step 5 — Frontend BackendConnection method

```javascript
async listAbstractClasses(datasetName, graphURI) {
    const url = `${PUBLIC_BACKEND_URL}/datasets/${encodeURIComponent(datasetName)}/graphs/${encodeURIComponent(graphURI)}/classes/abstract`;
    return fetch(url, {
        method: "GET",
        credentials: "include",
    });
}
```

## Step 6 — Reactive consumer

Either update the existing class-list reactive store or read on demand from a component. If the navigation tree needs to mark the abstract ones, extend `build-nav-object.js` to include the abstract flag on each class node, and update `ClassEntry.svelte` to render italic when the flag is set.

## Step 7 — Tests

Vitest unit tests for any new logic in `lib/`. Manual smoke test through the UI against a live Fuseki instance.

## Step 8 — PR

Follow the squash commit format and the pre-merge checklist in [`CONTRIBUTING.md`](https://github.com/SOPTIM/RDFArchitect/blob/main/.github/CONTRIBUTING.md). Update `CHANGELOG.md` under `## [Unreleased]` if the change is user-facing.
