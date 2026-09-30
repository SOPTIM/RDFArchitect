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

/** A dialog takes the focus over several frames while it opens, so it is claimed until it sticks. */
export function claimFocus(node, active) {
    const RETRIES = 6;
    let pending = null;

    focusWhenActive(active);

    function focusWhenActive(isActive) {
        cancel();
        if (!isActive) {
            return;
        }
        tryFocus(RETRIES);
    }

    function tryFocus(remaining) {
        pending = requestAnimationFrame(() => {
            pending = null;
            const control = node.querySelector("input, textarea");
            if (!control) {
                return;
            }
            if (document.activeElement !== control) {
                control.focus();
                putCaretAtEnd(control);
            }
            if (remaining > 0) {
                tryFocus(remaining - 1);
            }
        });
    }

    // Number inputs report a null selection and throw when one is set.
    function putCaretAtEnd(control) {
        if (control.selectionStart === null) {
            return;
        }
        const end = control.value.length;
        control.setSelectionRange(end, end);
    }

    function cancel() {
        if (pending !== null) {
            cancelAnimationFrame(pending);
            pending = null;
        }
    }

    return { update: focusWhenActive, destroy: cancel };
}
