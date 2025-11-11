# Consistent Structure for Svelte `<script>` Sections

This guide defines a consistent, readable order for content inside Svelte
`<script>` blocks (both instance scripts and `context="module"` scripts). It’s
based on recommendations from the Svelte documentation (declare props early,
keep reactivity clearly separated) and commonly used community guidelines for
component architecture.

## Target Order

1. **Imports & Re-exports** – all `import` statements, optional re-exports via
   `export { ... } from`.
2. **Type Definitions & File-Level Directives** – `type` and `interface`
   definitions, `enum`s, `//@ts-check`, `/// <reference ...>`.
3. **Component API** – props via Svelte 5 runes (`const { ... } = $props()`),
   optional `setContext` / `getContext`. Use `const` with runes;
   `let { ... } = $props()` is only needed if the variable will be reassigned
   later.
4. **Constants & Configuration** – immutable values (`const`), SvelteKit flags
   (`export const prerender = true;`), utility maps.
5. **State** – mutable values (`let`), stores, `$state` runes, `writable` /
   `readable`.
6. **Derived State** – `$derived(...)`, `derived(...)`.
7. **Reactive Effects** – `$effect(...)`, `watch(...)`.
8. **Lifecycle Hooks** – `onMount`, `beforeUpdate`, `afterUpdate`, `onDestroy`,
   `tick`.
9. **Functions & Handlers** – function declarations, `const fn = () => {}`, API
   calls, event handlers.
10. **Miscellaneous** – remaining top-level statements (for example direct calls
    or debug helpers).

Within each group the existing order is preserved to avoid losing semantic
proximity. For module scripts, step 3 is omitted; the rest of the order stays
the same.

Notes:

- Props must never be “hidden” by other statements – even a simple local
  variable (`let canvas;`) belongs in the state group and therefore must appear
  _after_ the props.
- `const` declarations that create internal state (`const foo = $state(...)`)
  also belong in the state group; pure configuration (`const API_URL = ...`)
  stays in the constants group.

## Rationale and Best Practices

- **Reading flow**: From outside to inside – first external dependencies, then
  the component’s public API, then internal state and logic.
- **Keep props visible**: Docs and examples consistently declare props at the
  start; in Svelte 5 the same applies to `$props`.
- **Clean reactivity**: State, derived values and effects are separated. This
  makes debugging easier and prevents unclear side effects.
- **Lifecycle hooks at the end**: Hooks often rely on values or functions
  declared earlier; with this order all dependencies are already available.

### Import Grouping

`eslint-plugin-import` (`import/order`) additionally enforces sorted imports:

- Groups: `[builtin, external]`, `[internal, parent, sibling, index]`, `[type]`.
- Aliased paths: `$lib/**` (internal, before other internal paths), `$env/**`
  (external, after other external packages).
- Alphabetical sorting (ascending, case-insensitive) with blank lines between
  groups.

## Example (Svelte 5 with Runes)

```svelte
<script>
    import { onMount } from "svelte";
    import { BackendConnection } from "$lib/api/backend.js";

    const { showDialog = $bindable() } = $props();

    const API_URL = "/api";
    let count = $state(0);

    const doubled = $derived(() => count * 2);
    const label = $derived(() => `Count: ${count}`);

    $effect(() => console.log(label));

    onMount(() => {
        fetch(API_URL);
    });

    function increment() {
        count += 1;
    }
</script>
```

## Usage

In the `frontend` project:

- Check only: `npm run lint`
- Check and fix: `npx eslint . --fix`

Prettier remains responsible for formatting; the structure described here is
enforced exclusively via ESLint.
