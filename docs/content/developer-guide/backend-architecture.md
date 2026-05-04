---
title: Backend Architecture
sidebar_position: 3
---

# Backend Architecture

The backend follows a deliberate **hexagonal / ports-and-adapters** layout. The dependency direction is always *outer → inner*:

```
   ┌──────────────────────────────────────────────────────────┐
   │ api/controller/        ← HTTP boundary (Spring MVC)      │
   │ api/dto/               ← request/response shapes         │
   │     │                                                    │
   │     ▼                                                    │
   │ services/<feature>/    ← use cases (interfaces) +        │
   │                          their implementations           │
   │     │                                                    │
   │     ▼                                                    │
   │ database/DatabasePort  ← port interface                  │
   │ database/inmemory/     ← active dataset store            │
   │ database/implementations/http/  ← Fuseki connection      │
   └──────────────────────────────────────────────────────────┘
```

## Use case interfaces

Every action exposed by the application is a one-method `*UseCase` interface, e.g.:

```java
public interface ListDatasetsUseCase {
    List<String> listDatasets();
}
```

A controller depends only on the use case interface; a service implements one or many use cases. This is a deliberate design choice — it keeps controllers small, makes individual operations easy to test, and lets services compose multiple ports without becoming god classes.

## Services

Services live under `services/<feature>/` and typically implement multiple use cases when they share state, repositories, or transaction boundaries. The `services/select/QueryDatasetService` is a good representative example — it implements `GetDatasetSchemaUseCase`, `ListGraphsUseCase`, `ListPrefixesUseCase`, and `ListDatasetsUseCase`, all of which need the same `DatabasePort`.

## The database port

`DatabasePort` is the only direct contact with the active dataset store. Runtime datasets live in the in-memory adapter; Fuseki is used through the database connection and snapshot port when snapshots are created or loaded:^

- **`database/implementations/http`** — talks to Fuseki over the SPARQL 1.1 protocol + Graph Store Protocol for snapshot operations.

The **in-memory** path (`database/inmemory`) is used heavily in tests and as the per-session working buffer for uploaded datasets and edits.

## REST controllers

Controllers are thin and follow a strict skeleton:

```java
@RestController
@RequestMapping("api/datasets")
@RequiredArgsConstructor
public class DatasetRESTController {

    private static final Logger logger = LoggerFactory.getLogger(...);

    private final ListDatasetsUseCase listDatasetsUseCase;
    private final DeleteDatasetUseCase deleteDatasetUseCase;

    @Operation(summary = "...", description = "...", tags = {...})
    @GetMapping
    public List<String> listDatasets(...) {
        logger.info("Received GET request: ...");
        var result = listDatasetsUseCase.listDatasets();
        logger.info("Sending response to GET ...");
        return result;
    }
}
```

Conventions worth knowing:

- **One controller per resource path tier.** `api/datasets`, `api/datasets/{name}/graphs`, `api/datasets/{name}/graphs/{uri}/classes`, etc. Controllers do not span tiers.
- **Always `@Operation` annotated** — Swagger UI is part of the public deliverable.
- **Always log on receive and on respond**, with the originating URL, dataset, and graph names where applicable. Audit log style.
- **Use cases are constructor-injected via `@RequiredArgsConstructor` (Lombok).** No field injection.
- **Read the `Origin` header into a parameter called `originURL`.** It is logged but never used for authorisation — auth lives outside the application.

## DTOs and mapping

DTOs live under `api/dto/` and are organised by feature (`attributes/`, `associations/`, `enumentries/`, `migration/`, `ontology/`, `packages/`, `rendering/`). They are flat data carriers — Lombok `@Value` or `@Data` — with no behaviour.

**MapStruct** generates DTO ↔ domain mappers. When you add a new DTO, also add the corresponding `*Mapper` interface and let MapStruct generate the implementation. The annotation processor runs as part of `mvn compile`.

## Exception handling

Domain exceptions live in `exception/<area>/` and are translated to HTTP responses by handlers in `exception/handlers/`. A new exception that needs a non-500 response *must* have a handler — there is no fallback that turns arbitrary exceptions into 4xx.

## SPARQL templates

Parameterised SPARQL queries live in `src/main/resources/sparql-templates/` and are loaded by classpath utility methods. The migration use cases use them heavily — see `sparql-templates/migration/*.sparql` for the templates that the wizard composes into the final UPDATE script. Keep templates here rather than inline string concatenation in Java.
