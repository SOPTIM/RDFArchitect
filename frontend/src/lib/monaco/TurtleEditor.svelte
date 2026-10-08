<!--
  -    Copyright (c) 2024-2026 SOPTIM AG
  -
  -    Licensed under the Apache License, Version 2.0 (the "License");
  -    you may not use this file except in compliance with the License.
  -    You may obtain a copy of the License at
  -
  -        http://www.apache.org/licenses/LICENSE-2.0
  -
  -    Unless required by applicable law or agreed to in writing, software
  -    distributed under the License is distributed on an "AS IS" BASIS,
  -    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  -    See the License for the specific language governing permissions and
  -    limitations under the License.
  -
  -->

<script>
    import { untrack } from "svelte";

    import { openText, pushExternalText } from "./externalText.js";
    import { MARKER_OWNER, toMarkers } from "./markers.js";
    import { loadMonaco, TURTLE_LANGUAGE_ID } from "./monaco.js";
    import { resolveThemeName } from "./theme.js";
    import {
        attachTermSource,
        detachTermSource,
    } from "./turtleLanguageFeatures.js";

    import { browser } from "$app/environment";

    let {
        value = $bindable(""),
        readOnly = false,
        findings = [],
        autoGrow = false,
        minHeight = 90,
        maxHeight = 480,
        onSave = undefined,
        onchange = undefined,
        termSource = undefined,
        /** Called with the cursor's line when the reader asks to see it in the form view. */
        onshowinform = undefined,
        /**
         * Which document `value` belongs to. A change opens the new text with a history of its
         * own; without one, every new value is an undoable edit to the text before it.
         */
        documentKey = undefined,
        /** Shown while the text is empty, to say what the box is for. */
        placeholder = undefined,
    } = $props();

    // All four are $state because the effects below key off them: the editor is created when
    // the container element appears, and the marker effect has to re-run once there is an editor
    // to attach markers to.
    let container = $state(null);
    let monaco = $state(null);
    let editor = $state(null);
    let failure = $state(null);

    /** Height of the auto-growing box; ignored when the parent sizes the editor. */
    let grownHeight = $state(minHeight);

    /**
     * The theme Monaco should be painting in.
     *
     * Held as state rather than called where it is needed, because it depends on two things that
     * are reactive in different ways: the user setting, which lives in `userSettings`, and the OS
     * preference, which `matchMedia` only reports through an event. Folding both into one value
     * keeps the effect that applies it reading a single dependency.
     */
    let themeName = $state(resolveThemeName());

    /** Set while a new document is being pushed in, to tell that apart from a keystroke. */
    let applyingExternalChange = false;

    /** The `documentKey` whose text the editor holds. */
    let shownKey = undefined;

    /**
     * Where to put the cursor once the editor exists and holds the text being revealed in — a link
     * can ask for a line before Monaco has finished loading.
     */
    let pendingReveal = $state(null);

    $effect(() => {
        if (!browser || !container) {
            return;
        }
        let disposed = false;
        let created;

        loadMonaco()
            .then(api => {
                if (disposed) {
                    return;
                }
                monaco = api;
                created = api.editor.create(container, {
                    value: value ?? "",
                    language: TURTLE_LANGUAGE_ID,
                    theme: resolveThemeName(),
                    readOnly,
                    placeholder,
                    automaticLayout: true,
                    fontSize: 13,
                    minimap: { enabled: !autoGrow },
                    lineNumbers: "on",
                    renderLineHighlight: autoGrow ? "none" : "line",
                    scrollBeyondLastLine: !autoGrow,
                    scrollbar: autoGrow
                        ? { alwaysConsumeMouseWheel: false }
                        : {},
                    wordWrap: "off",
                    tabSize: 4,
                    // The generated and imported documents both use plain spaces; keeping the
                    // editor from inserting tabs means a round-trip does not reformat the file.
                    insertSpaces: true,
                    fixedOverflowWidgets: true,
                });
                editor = created;

                // The way back from the text to the form. Monaco owns this context menu, so the
                // entry has to be an editor action rather than one of the app's own menus.
                created.addAction({
                    id: "rdfa.shacl.showInForm",
                    label: "Show in the Form view",
                    contextMenuGroupId: "navigation",
                    contextMenuOrder: 1.6,
                    run: instance =>
                        onshowinform?.(instance.getPosition()?.lineNumber ?? 1),
                });

                created.onDidChangeModelContent(() => {
                    value = created.getValue();
                    // Monaco reports a programmatic setValue the same way it reports typing, so
                    // only an edit the user made is worth revalidating for.
                    if (!applyingExternalChange) {
                        onchange?.();
                    }
                });
                if (autoGrow) {
                    created.onDidContentSizeChange(() => {
                        grownHeight = Math.min(
                            Math.max(created.getContentHeight(), minHeight),
                            maxHeight,
                        );
                    });
                }
                // An action rather than addCommand: a command's keybinding is global and outlives
                // the editor, so every remount added another one still calling into the old page.
                if (onSave) {
                    created.addAction({
                        id: "rdfa.shacl.save",
                        label: "Save",
                        keybindings: [api.KeyMod.CtrlCmd | api.KeyCode.KeyS],
                        run: () => onSave?.(),
                    });
                }
            })
            .catch(error => {
                console.warn("Failed to load the editor:", error);
                failure = "The editor could not be loaded.";
            });

        return () => {
            disposed = true;
            created?.getModel()?.dispose();
            created?.dispose();
            if (created === editor) {
                editor = null;
                shownKey = undefined;
            }
        };
    });

    /**
     * Pushes an external change into the editor.
     *
     * Within one document it is an edit on the model rather than a `setValue`, so a form edit stays
     * undoable and the view does not jump — see `pushExternalText`. Another document replaces the
     * model's text and history outright — see `openText`.
     */
    $effect(() => {
        const next = value ?? "";
        const key = documentKey;
        if (!editor) {
            return;
        }
        applyingExternalChange = true;
        try {
            if (key !== shownKey) {
                openText(editor, next);
                shownKey = key;
            } else {
                pushExternalText(editor, next);
            }
        } finally {
            applyingExternalChange = false;
        }
    });

    // Declared after the text effect so that, when both are due, the text is in first.
    $effect(() => {
        const target = pendingReveal;
        const current = editor;
        void value;
        void documentKey;
        if (!target || !current) {
            return;
        }
        untrack(() => {
            pendingReveal = null;
            // The editor may have been hidden until a moment ago, and a hidden editor has no size
            // to centre anything in.
            current.layout();
            const position = { lineNumber: target.line, column: target.column };
            current.revealPositionInCenterIfOutsideViewport(position);
            current.setPosition(position);
            current.focus();
        });
    });

    $effect(() => {
        editor?.updateOptions({ readOnly, placeholder });
    });

    // Completion and hover answer only for a model that has been given a schema to answer from,
    // so the small editors in the class-editor dialogs stay plain.
    $effect(() => {
        const model = editor?.getModel();
        if (!model || !termSource) {
            return;
        }
        attachTermSource(model, termSource);
        return () => detachTermSource(model);
    });

    $effect(() => {
        // Reads the user setting, so a change to it re-runs this.
        themeName = resolveThemeName();
    });

    $effect(() => {
        if (!browser || typeof window.matchMedia !== "function") {
            return;
        }
        const query = window.matchMedia("(prefers-color-scheme: dark)");
        const onChange = () => (themeName = resolveThemeName());
        query.addEventListener("change", onChange);
        return () => query.removeEventListener("change", onChange);
    });

    // Monaco's theme is global rather than per editor, so changing the setting repaints every
    // open editor at once — which is what the user asking for a dark editor means.
    $effect(() => {
        monaco?.editor.setTheme(themeName);
    });

    $effect(() => {
        const model = editor?.getModel();
        if (!monaco || !model) {
            return;
        }
        monaco.editor.setModelMarkers(
            model,
            MARKER_OWNER,
            toMarkers(findings ?? [], SEVERITIES(), (line, column) => {
                if (line > model.getLineCount()) {
                    return column + 1;
                }
                const word = model.getWordAtPosition({
                    lineNumber: line,
                    column,
                });
                return word?.endColumn ?? model.getLineMaxColumn(line);
            }),
        );
    });

    function SEVERITIES() {
        return {
            ERROR: monaco.MarkerSeverity.Error,
            WARNING: monaco.MarkerSeverity.Warning,
            INFO: monaco.MarkerSeverity.Info,
        };
    }

    /**
     * Scrolls to a position and puts the cursor on it. Used by the problems panel.
     *
     * Applied once the editor exists and has taken in the current text, so a reveal asked for
     * while Monaco is still loading, or in the same moment a document is opened, is not lost.
     */
    export function reveal(line, column = 1) {
        pendingReveal = { line, column };
    }

    export function focusEditor() {
        editor?.focus();
    }
</script>

<!--
  @component
  A Turtle/SHACL editor.

  Two shapes of use, which is why the sizing is a prop rather than a class on the container:
  the constraints workbench gives it a pane to fill, while the class-editor dialogs show a few
  lines of a single shape and want the box to grow with its content (`autoGrow`).

  Findings are passed in as `findings` and shown as squiggles; the component does not validate
  anything itself. Phase 4 replaces the caller's REST validation with a language server without
  this component changing.
-->

{#if failure}
    <div
        class="text-red-text bg-red-background border-red-border rounded border p-3 text-sm"
    >
        {failure}
    </div>
{:else}
    <div
        bind:this={container}
        class="border-border h-full w-full overflow-hidden border"
        style={autoGrow ? `height: ${grownHeight}px` : undefined}
    ></div>
{/if}
