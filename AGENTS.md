# Agent Instructions

The key words MUST, MUST NOT, SHOULD, SHOULD NOT and MAY are used as described in RFC 2119 and RFC 8174.

Details on everything below are in `docs/content/developer-guide/` and `.github/CONTRIBUTING.md`.

Everything in the repository, as well as commit messages, PR titles and PR descriptions, MUST be in English.

## Setup

- Toolchain versions are listed under Prerequisites in `README.md`.
- After a fresh checkout, run `npm run api:generate` in `frontend/`. The generated API client is git-ignored, and frontend tests, lint and build fail without it.

## Architecture

### Backend

The backend follows a hexagonal architecture (`docs/content/developer-guide/backend-architecture.md`). For new code:

- Each new action MUST get its own `*UseCase` interface with one method, implemented by a service in the matching package under `services/`.
- Controllers MUST depend only on use-case interfaces.
- New SPARQL queries MUST be separate files under `backend/src/main/resources/sparql-templates/`, loaded via `SparqlTemplateLoader`, not inline in Java.
- Existing code has deviations (use cases with several methods, packages by operation, inline queries). New code MUST NOT copy them.

### Frontend

The frontend is described in `docs/content/developer-guide/frontend-architecture.md`. For new code:

- Backend calls MUST go through the generated API client or a store, not through hand-written `fetch` calls.
- New validation functions SHOULD be added to `frontend/src/lib/models/reactive/validity-rules/validityFunctions.js`, and existing ones there SHOULD be reused.
- Global selections, such as the selected workspace, schema or class, SHOULD be read from `frontend/src/lib/sharedState.svelte.js` instead of being passed down as props.

## Code

### General

- Code SHOULD NOT be duplicated. Existing helpers SHOULD be reused, and new code SHOULD be written to be reusable.
- Readability and maintainability SHOULD take priority over brevity and cleverness.

### Comments

- Comments SHOULD only be added where the code is not self-explanatory.
- A comment MAY explain a non-obvious reason, such as a workaround or an external constraint.
- Comments SHOULD use plain language, short sentences and the present tense, and describe behavior ("Returns …", "Skips …"), without "we" or "I". Bullet points are fine.
- Comments MUST NOT restate the code. If a comment would only repeat it, improve the name instead.
- Comments MUST NOT narrate the change, justify decisions, or refer to previous code ("previously …").
- Comments MUST NOT contain ticket, PR or author references.
- Decorative comments (banners, separators, `} // end if`) SHOULD NOT be used.
- Commented-out code MUST NOT be committed. Delete unused code.
- When code changes, its comments and JavaDoc MUST be updated or removed in the same change.
- Tests MAY use `// given`, `// when`, `// then` as structure markers.

```java
// Good: The external service rejects more than 100 IDs per request, so they are sent in batches.
// Bad:  Loop over all classes and add them to the list.
// Bad:  Changed to a map because the list was too slow.
```

### JavaDoc and SPARQL

- In the backend, every new or changed method of an interface MUST have JavaDoc: one summary sentence, `@param` for each parameter, `@return` unless `void`, and `@throws` for exceptions the caller has to handle.
- New `.sparql` files MUST start with a short `#` comment stating what the query does and which parameters it expects.

## Generated files

These files are generated and MUST NOT be edited by hand. When the trigger applies, run the given command.

- `CHANGELOG.md`: generated from squash commit titles by the `Update Changelog` workflow at release time.
- `frontend/openapi.json`: after changing REST routes or DTOs, run `mvn -B compile exec:java@export-openapi` in `backend/` and commit the result; CI checks it.
- `frontend/src/lib/api/generated/`: after `frontend/openapi.json` changes, run `npm run api:generate` in `frontend/`.
- `backend/LICENSES-THIRD-PARTY.md`: after changing dependencies in `backend/pom.xml`, run `mvn -B org.codehaus.mojo:license-maven-plugin:add-third-party` in `backend/`; CI checks it.
- `frontend/LICENSES-THIRD-PARTY.md`: after changing dependencies in `frontend/package.json`, run `npm run licenses:generate` in `frontend/`; lint checks it.

## Documentation

- Changes to behavior, API or setup MUST update the documentation in the same change:
  - user-visible behavior: `docs/content/user-guide/`
  - installation, configuration, operations: `docs/content/admin-guide/`
  - architecture, CI, code style: `docs/content/developer-guide/`
  - quickstart, prerequisites, configuration highlights, API client: `README.md`
- New pages MUST be added to `docs/sidebars.js`.
- Documentation MUST describe the current state only, without history ("previously …", "since v1.7 …").
- Documentation SHOULD be descriptive: what exists and how to use it, not background or motivation.
- Documentation SHOULD use short sentences and prefer bullet points.

## Tests and checks

### Test names

- Tests in the backend and the frontend SHOULD be named `<method>_<condition>_<expectedResult>` in camelCase:
  - backend: `void listSchemaNames_emptyCollection_returnsEmptyList()`
  - frontend: `test("listSchemaNames_emptyCollection_returnsEmptyList", ...)`

### Running tests

- Backend, in `backend/`: `mvn -B test -Dtest=<ClassName>`, all unit tests with `mvn -B test`.
- Frontend, in `frontend/`: `npm run test -- <path>`, all tests with `npm run test`.

### Before committing

- In `backend/`: `mvn -B spotless:apply`, then `mvn -B -Plint -DskipTests verify`.
- In `frontend/`: `npm run format`, then `npm run lint`.

## Commits

Commits follow [Conventional Commits 1.0.0](https://www.conventionalcommits.org/en/v1.0.0/) and `.github/CONTRIBUTING.md`.

### Format

- Commit messages and PR titles MUST use `<type>[optional scope][!]: <description> (#<pr>, RDFA-<id>, GH-<issue>)`. Omit `#<pr>` while no PR exists. The PR title becomes the squash commit and the changelog entry.
- The type MUST be one of `feat`, `fix`, `docs`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `style`.
- The description MUST start with a lowercase imperative verb, without a trailing period. It SHOULD be short and specific.
- Breaking changes MUST have `!` in the header; only these are listed under Breaking Changes in the changelog. A `BREAKING CHANGE:` footer MAY add details.

### Content

- Commit bodies and PR descriptions SHOULD be limited to the relevant changes and written as bullet points, not prose.
- Every commit MUST be signed off (`git commit -s`) for the DCO.
