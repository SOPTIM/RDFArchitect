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
 * Putting text into an editor that somebody else produced.
 *
 * Separate from the component so it can be tested without Monaco, which cannot be loaded in a
 * test environment.
 */

/**
 * Replaces the editor's text with `next`, keeping the undo stack and the view.
 *
 * `setValue` is the obvious call and the wrong one: it discards the model's undo stack and scrolls
 * back to the top. Everything that writes the buffer from outside the editor went through it — a
 * form edit above all — so a form edit could not be undone with Ctrl+Z, and switching to the
 * Turtle view after one landed you at the start of the file instead of at the shape you had been
 * looking at.
 *
 * An edit operation over the whole model does the same replacement as part of the model's history.
 * The stack elements around it keep it as one undo step rather than merging it into what was typed
 * before; the view state is put back because replacing every line moves the cursor and the scroll
 * position with it.
 *
 * @returns whether the text was changed. Text the editor already holds is left alone, so a
 *     keystroke — which sets the bound value from the editor itself — does not bounce back as an
 *     edit that would move the cursor.
 */
export function pushExternalText(editor, next) {
    const model = editor?.getModel?.();
    if (!editor || !model || model.getValue() === next) {
        return false;
    }
    const viewState = editor.saveViewState();
    model.pushStackElement();
    model.pushEditOperations(
        [],
        [{ range: model.getFullModelRange(), text: next }],
        () => null,
    );
    model.pushStackElement();
    if (viewState) {
        editor.restoreViewState(viewState);
    }
    return true;
}
