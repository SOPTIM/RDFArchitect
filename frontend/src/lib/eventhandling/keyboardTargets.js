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
 * Where a key press belongs to what is focused rather than to the application.
 *
 * The global handler in `+layout.svelte` calls `preventDefault()` on anything a shortcut claims,
 * so a target listed here is the difference between typing and triggering a command somewhere
 * else on the page.
 */

/**
 * Widgets that answer for their own keys, matched by class because they are not form controls.
 *
 * `.monaco-editor` covers the code editor and the widgets it renders inside itself — the
 * suggestion list, the find box, the hover; `.monaco-menu` covers its context menu, which is
 * portalled to the document body.
 */
const SELF_HANDLING = ".monaco-editor, .monaco-menu";

/**
 * Whether the event's target handles its own keyboard input.
 *
 * Monaco is on the list for two reasons that together produced a copy that copied a class.
 * With `editContext` enabled — the default since 0.56 — the focused element is a plain `div`,
 * so a tag-name test does not recognise it as somewhere text is being typed. And in a browser
 * Monaco deliberately registers no keybinding at all for cut, copy and paste, leaving them to
 * the native clipboard events; `preventDefault()` on Ctrl+C therefore does not merely shadow the
 * editor's copy, it stops the browser from ever raising the copy event.
 */
export function ownsKeyboardInput(target) {
    if (!(target instanceof HTMLElement)) {
        return false;
    }
    return (
        target.isContentEditable ||
        target.tagName === "INPUT" ||
        target.tagName === "TEXTAREA" ||
        target.tagName === "SELECT" ||
        target.closest(SELF_HANDLING) !== null
    );
}
