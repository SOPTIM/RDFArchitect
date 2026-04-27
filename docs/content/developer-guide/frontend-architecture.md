---
title: Frontend Architecture
sidebar_position: 6
---

# Frontend Architecture

The frontend is a SvelteKit 2 application using Svelte 5 runes, Vite 7, Tailwind 4, and Bits UI 2. It is a single-page editor with a small number of routes.

## Stack at a glance

| Concern | Choice |
| ------- | ------ |
| Framework | Svelte 5 (runes) + SvelteKit 2 |
| Bundler | Vite 7 |
| Styling | Tailwind 4 + theme tokens in `app.css` |
| Modal primitives | Bits UI 2 |
| Diagram engine | SvelteFlow + custom layouting |
| Lint | ESLint with custom rules |
| Test | Vitest |
| State | Svelte runes + custom reactive wrappers + shared-state singletons |

## Route map

The route directories under `frontend/src/routes/` correspond directly to the URL paths:

| Path | Source | Purpose |
| ---- | ------ | ------- |
| `/` | `routes/+page.svelte` | Welcome page. |
| `/mainpage` | `routes/mainpage/` | The editor (diagram, navigation, class editor). |
| `/changelog` | `routes/changelog/` | Per-schema history with restore. |
| `/compare` | `routes/compare/` | Comparison view. |
| `/migrate` | `routes/migrate/` | Migration wizard. |
| `/shacl` | `routes/shacl/` | SHACL inspection dialogs. |
| `/layout` | `routes/layout/` | Shared layout primitives, top toolbar. |

Cross-cutting dialogs (import/export, namespace management, snapshot, new class/package, dataset/graph deletion, search) live next to the route they're invoked from, or at the routes root for the dialogs used from multiple places.

## Reactive wrappers

The frontend wraps the backend's plain JSON shapes in runes-backed Svelte classes, one per domain entity (class, attribute, association, namespace, package, shape). This is what lets components mutate fields with `$state`-like ergonomics while keeping serialization clean.

Look for the wrapper files under `frontend/src/lib/models/` to see the convention before introducing a new entity.

## Backend communication

A single API wrapper in `frontend/src/lib/api/` is the only place that constructs URLs to the backend. Components import it and call methods on it.

Three rules:

1. **Always go through the API wrapper.** Don't build URLs by hand.
2. **Preserve `credentials: "include"`** on requests. Required for the session cookie.
3. **Use the runtime config** (`PUBLIC_BACKEND_URL`) — never hard-code `http://localhost:8080`.

Adding a new endpoint means adding the matching method to the wrapper.

## State and data flow

Three layers of state, in order of growing scope:

| Layer | Where | Lifetime |
| ----- | ----- | -------- |
| **Component-local** | Inside the component file. | Component lifetime. |
| **Route-shared** | Reactive instances passed via context. | Route lifetime. |
| **Cross-route** | Shared-state singletons in `frontend/src/lib/`. | Browser session. |

There is no Redux/Pinia/Zustand-style global store. Don't add one. Reuse the existing primitives.

## Validity rules

Per-component validation logic lives next to the component. The pattern is a pure function that takes the current state and returns either `null` or an array of error messages. The component renders the messages inline and disables save while there are errors.

## Script-block ordering convention

Svelte components in this repo follow a strict ordering of `<script>` content, documented in `frontend/docs/script-structure.md`:

1. Imports.
2. `$props` declaration.
3. Local `$state`.
4. `$derived`.
5. Functions.
6. `$effect`.

ESLint enforces this. Don't fight it — the linearity makes diffs much easier to read.

## Custom ESLint rules

Custom rules live in `frontend/eslint-rules/`. They cover the SPDX license header, the script-block ordering above, and the rule that backend access must go through the API wrapper. Read them when in doubt about why a lint warning fires.

## Styling

Tailwind 4 with theme tokens defined in `src/app.css`. Components reach for utility classes; design tokens are referenced via CSS variables. Don't add ad-hoc colors — extend the token list.

## Diagram rendering

`src/lib/rendering/` holds the SvelteFlow-based renderer for class boxes and edges, the custom layouter for fresh packages, and the helpers that translate model objects into SvelteFlow nodes and edges.

When a model changes, the rendering layer subscribes to the relevant reactive wrappers and recomputes only the affected parts.

## Internationalisation

There is no i18n infrastructure currently — UI strings are inline English. If you add a feature, keep strings in template literals; don't bolt on per-component translation tables in the meantime.
