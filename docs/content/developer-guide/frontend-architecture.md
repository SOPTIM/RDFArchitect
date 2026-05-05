---
title: Frontend Architecture
sidebar_position: 4
---

# Frontend Architecture

## Stack

- **SvelteKit** (Svelte 5 with runes — `$state`, `$derived`, `$effect`, `$props`, `$bindable`).
- **Vite** for build and dev server.
- **Tailwind CSS** for styling, with project-specific design tokens (CSS variables) in `src/lib/styles/`.
- **bits-ui** for headless dialog, menubar, and dropdown primitives, wrapped in project-local components under `src/lib/components/bitsui/`.
- **SvelteFlow** for diagram rendering, with **elkjs** for auto-layout.
- **Mermaid** as the alternative renderer.
- **CodeMirror 6** with `codemirror-lang-turtle` for the TTL editors.
- **Asciidoctor.js** for rendering class comments.
- **Vitest** for unit tests, **jsdom** for the DOM environment.

## Routes

SvelteKit's filesystem routing is used straightforwardly:

```
/                  →  Homepage (routes/+page.svelte)
/mainpage          →  Main editor (left tree + diagram + right class editor)
/changelog         →  Edit history view
/compare           →  Compare results view
/migrate           →  5-step migration wizard
/shacl/...         →  SHACL views
```

The editor listens to URL parameters `?dataset=...&graph=...&package=...` to support deep links.

## Reactive models

A central pattern: every editable domain object has a **reactive wrapper** in `lib/models/reactive/models/` (e.g. `reactive-class.svelte.js`, `reactive-namespace.svelte.js`, `reactive-ontology.svelte.js`). These wrappers:

- Hold the original DTO and a working copy.
- Track `isModified`, `isValid`, and per-field violations as `$derived` runes.
- Expose `save()`, `reset()`, and field accessors.
- Drive the inline validation visible in dialogs.

**DTO ↔ reactive object mapping** lives in `lib/models/reactive/mapper/`. Whenever you add a new field to the backend that the frontend needs to edit, the chain to update is:

1. Backend DTO.
2. Frontend type in `lib/models/dto/`.
3. Reactive wrapper in `lib/models/reactive/models/`.
4. DTO ↔ reactive mapper.
5. UI component that exposes the field.

## Validity rules

`lib/models/reactive/validity-rules/validityFunctions.js` is the single home for cross-cutting validation (label uniqueness, prefix uniqueness, valid IRIs, valid NCNames, etc.). Reuse what's there before adding a new function.

## Backend communication

`BackendConnection` in `lib/api/backend.js` is a hand-written class with one method per backend endpoint. Every method:

- Builds the URL from `PUBLIC_BACKEND_URL`.
- Uses `credentials: "include"` so the session cookie will be send.
- Returns the raw `Response` — callers decide whether to `.json()`, `.text()`, or `.blob()`.

When you add a new backend endpoint, add the matching method here. Do not call `fetch()` directly from a component — the indirection makes mocking in tests possible and keeps URLs in one place.

## Shared state

`lib/sharedState.svelte.js` exports cross-component reactive state, primarily:

- `editorState.selectedDataset` / `selectedGraph` / `selectedPackageUUID` / `selectedClassUUID`
- `forceReloadTrigger` — a "kick everything to refresh" signal used after destructive actions
- `compareState`, `migrationState` — wizard state passed across pages

Use these instead of inventing per-component prop drilling for global selections.

## Svelte script ordering

The repository enforces a specific script-block ordering (imports → props → constants → state → derived → effects → lifecycle → functions). The full rationale and ESLint rule are in [`frontend/docs/script-structure.md`](https://github.com/SOPTIM/RDFArchitect/blob/main/frontend/docs/script-structure.md). The custom rule lives at `frontend/eslint-rules/rules/svelte-script-order/`. When in doubt, run `npm run format` and follow whatever the auto-fixer produces.

## Custom ESLint rules

Three custom rules ship with the project:

- `copyright-header` — enforces the Apache 2.0 header on every source file.
- `svelte-file-structure` — order of `<script>`, markup, `<style>`.
- `svelte-script-order` — order *inside* `<script>` blocks.

If you write a new file, the auto-fixer (`npm run format`) inserts the header for you.
