/*
 *    Copyright (c) 2024-2026 SOPTIM AG
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

/**
 * Enough of Monaco to run the editor component in jsdom, where the real one cannot load.
 *
 * Where the component depends on Monaco's behaviour, the fake behaves the same way:
 *
 * - A text that is set gets one line ending throughout, the one most of its lines use, and loses
 *   a leading byte order mark. An edit is written in the model's line ending.
 * - Every change is reported to the content listeners at once, typing and outside changes alike.
 * - Edits collect in one undo step until `pushStackElement` closes it; `setValue` starts a new
 *   history. The alternative version id comes back to the same number on undo, but only after the
 *   editor's content listeners have run: they see a new number, and the model's own see the old.
 * - Markers move down with lines inserted above them, and a text that is set drops them.
 *
 * A test file mocks `$lib/monaco/monaco.js` with `loadMonaco: () => Promise.resolve(fake.api)` and
 * reads `fake.state` for the editor, its model and the markers. Each test file gets its own.
 */
export const fake = createFakeMonaco();

function createFakeMonaco() {
    const state = {
        editor: null,
        model: null,
        markers: [],
        contentHeight: 100,
        sizeListeners: [],
    };

    const api = {
        KeyMod: { CtrlCmd: 0 },
        KeyCode: { KeyS: 0 },
        MarkerSeverity: { Error: 8, Warning: 4, Info: 2 },
        editor: {
            create: (_container, options) => {
                state.markers = [];
                state.sizeListeners = [];
                state.model = createModel(state, options.value ?? "");
                state.editor = createEditor(state, state.model);
                return state.editor;
            },
            setTheme: () => {},
            setModelMarkers: (model, _owner, markers) => {
                // Monaco keeps a marker only if its line exists in the text at the time.
                state.markers = markers
                    .filter(
                        marker =>
                            model.getValue() !== "" &&
                            marker.startLineNumber <= model.getLineCount(),
                    )
                    .map(marker => ({ ...marker }));
            },
        },
    };

    return { state, api };
}

function createModel(state, initial) {
    const listeners = new Set();
    const editorListeners = new Set();
    let text = "";
    let eol = "\n";
    let bom = "";
    let versionId = 1;
    let alternativeVersionId = 1;
    let undoStack = [];
    let redoStack = [];
    let openStep = false;

    function load(next) {
        bom = next.startsWith("\uFEFF") ? "\uFEFF" : "";
        const body = next.slice(bom.length);
        const breaks = body.match(/\r\n|\r|\n/g) ?? [];
        const carriageReturns = breaks.filter(br => br !== "\n").length;
        eol = carriageReturns > breaks.length / 2 ? "\r\n" : "\n";
        text = body.replace(/\r\n|\r|\n/g, eol);
    }

    function snapshot() {
        return { text, eol, alternativeVersionId };
    }

    function changed(event, restoredAlternativeVersionId) {
        versionId += 1;
        alternativeVersionId = versionId;
        editorListeners.forEach(listener => listener(event));
        alternativeVersionId = restoredAlternativeVersionId ?? versionId;
        listeners.forEach(listener => listener(event));
    }

    function lines() {
        return text.split(eol);
    }

    function offsetAt(lineNumber, column) {
        const all = lines();
        let offset = 0;
        for (let line = 1; line < lineNumber; line += 1) {
            offset += all[line - 1].length + eol.length;
        }
        return offset + column - 1;
    }

    function isWhole(range) {
        return (
            range.startLineNumber === 1 &&
            range.startColumn === 1 &&
            range.endLineNumber >= lines().length &&
            range.endColumn > lines().at(-1).length
        );
    }

    function moveMarkers(range, inserted) {
        if (isWhole(range)) {
            state.markers = [];
            return;
        }
        const delta =
            inserted.split(eol).length -
            1 -
            (range.endLineNumber - range.startLineNumber);
        state.markers = state.markers.map(marker =>
            marker.startLineNumber > range.endLineNumber ||
            (marker.startLineNumber === range.endLineNumber &&
                marker.startColumn >= range.endColumn)
                ? {
                      ...marker,
                      startLineNumber: marker.startLineNumber + delta,
                      endLineNumber: marker.endLineNumber + delta,
                  }
                : marker,
        );
    }

    const model = {
        getValue: (_eol, preserveBOM = false) =>
            (preserveBOM ? bom : "") + text,
        getEOL: () => eol,
        getLineCount: () => lines().length,
        getLineContent: line => lines()[line - 1],
        getLineMaxColumn: line => lines()[line - 1].length + 1,
        getWordAtPosition: () => null,
        getFullModelRange: () => ({
            startLineNumber: 1,
            startColumn: 1,
            endLineNumber: lines().length,
            endColumn: lines().at(-1).length + 1,
        }),
        getAlternativeVersionId: () => alternativeVersionId,
        setValue: next => {
            load(next);
            undoStack = [];
            redoStack = [];
            openStep = false;
            state.markers = [];
            changed({ whole: true });
        },
        pushStackElement: () => {
            openStep = false;
        },
        pushEditOperations: (_selections, edits) => {
            if (!openStep) {
                undoStack.push(snapshot());
                openStep = true;
            }
            redoStack = [];
            const whole = edits.some(({ range }) => isWhole(range));
            // Applied back to front, so an earlier edit's offsets still hold.
            [...edits]
                .sort(
                    (a, b) =>
                        offsetAt(b.range.startLineNumber, b.range.startColumn) -
                        offsetAt(a.range.startLineNumber, a.range.startColumn),
                )
                .forEach(({ range, text: replacement }) => {
                    const inserted = (replacement ?? "").replace(
                        /\r\n|\r|\n/g,
                        eol,
                    );
                    const start = offsetAt(
                        range.startLineNumber,
                        range.startColumn,
                    );
                    const end = offsetAt(range.endLineNumber, range.endColumn);
                    moveMarkers(range, inserted);
                    text = text.slice(0, start) + inserted + text.slice(end);
                });
            changed({ whole });
            return null;
        },
        undo: () => step(undoStack, redoStack),
        redo: () => step(redoStack, undoStack),
        onDidChangeContent: listener => {
            listeners.add(listener);
            return { dispose: () => listeners.delete(listener) };
        },
        /** How the editor hears of a change: see `changed`. */
        onEditorChange: listener => {
            editorListeners.add(listener);
            return { dispose: () => editorListeners.delete(listener) };
        },
        dispose: () => {
            listeners.clear();
            editorListeners.clear();
        },
    };

    function step(from, to) {
        openStep = false;
        const target = from.pop();
        if (!target) {
            return;
        }
        to.push(snapshot());
        ({ text, eol } = target);
        changed({ whole: true }, target.alternativeVersionId);
    }

    load(initial);
    return model;
}

function createEditor(state, model) {
    let position = { lineNumber: 1, column: 1 };
    let scrollTop = 0;
    // Replacing the whole text takes the cursor and the scroll position with it.
    model.onEditorChange(({ whole }) => {
        if (whole) {
            position = { lineNumber: 1, column: 1 };
            scrollTop = 0;
        }
    });

    /** What typing does: one undo step of its own. */
    function edit(edits) {
        model.pushStackElement();
        model.pushEditOperations([], edits, () => null);
        model.pushStackElement();
    }

    return {
        getModel: () => model,
        getValue: () => model.getValue(),
        onDidChangeModelContent: listener => model.onEditorChange(listener),
        onDidContentSizeChange: listener => {
            state.sizeListeners.push(listener);
            return { dispose: () => {} };
        },
        getContentHeight: () => state.contentHeight,
        addAction: () => ({ dispose: () => {} }),
        updateOptions: () => {},
        executeEdits: (_source, edits) => {
            edit(edits);
            return true;
        },
        trigger: (_source, handlerId) => {
            if (handlerId === "undo") {
                model.undo();
            } else if (handlerId === "redo") {
                model.redo();
            }
        },
        getPosition: () => position,
        setPosition: next => (position = { ...next }),
        getScrollTop: () => scrollTop,
        setScrollPosition: next => (scrollTop = next.scrollTop ?? scrollTop),
        saveViewState: () => ({ position, scrollTop }),
        restoreViewState: view => {
            position = view.position;
            scrollTop = view.scrollTop;
        },
        focus: () => {},
        layout: () => {},
        revealPositionInCenterIfOutsideViewport: () => {},
        dispose: () => {},
    };
}
