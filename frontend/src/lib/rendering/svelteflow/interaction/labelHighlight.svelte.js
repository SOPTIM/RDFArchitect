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

import { labelNodeId } from "../diagram/labelNodes.js";

/**
 * The label a user is pressing, so its association can light up to show which one the label
 * belongs to. Module state rather than something the diagram owns, because the edge components are
 * rendered by SvelteFlow and receive no props from it.
 *
 * A press lights the association and a release lets it go, which covers a click and a drag alike:
 * both start on pointer down and end on pointer up.
 */
export const labelHighlight = createLabelHighlight();

/**
 * How long a press stays lit at the least. A click is over within a few milliseconds, so without a
 * floor the association would only blink. Kept short on purpose: the edge rises into the highlight
 * and decays out of it on its own, and this is only the plateau in between, which reads as the
 * edge being stuck once it grows.
 */
const MIN_HIGHLIGHT_DURATION = 350;

function createLabelHighlight() {
    let heldLabelId = $state(null);
    let pressedAt = 0;
    let releaseTimeout = null;

    function matches(labelId, labels) {
        return (
            !!labelId &&
            (labels ?? []).some(label => labelNodeId(label) === labelId)
        );
    }

    function clear() {
        if (releaseTimeout !== null) {
            clearTimeout(releaseTimeout);
            releaseTimeout = null;
        }
        heldLabelId = null;
    }

    return {
        /** Lights the association of a label up, for as long as the label is pressed. */
        press(labelId) {
            clear();
            heldLabelId = labelId;
            pressedAt = Date.now();
        },
        /** Ends a press, keeping the light on for the rest of the minimum duration. */
        release() {
            if (heldLabelId === null || releaseTimeout !== null) {
                return;
            }
            const remaining = MIN_HIGHLIGHT_DURATION - (Date.now() - pressedAt);
            if (remaining <= 0) {
                clear();
                return;
            }
            releaseTimeout = setTimeout(() => {
                releaseTimeout = null;
                heldLabelId = null;
            }, remaining);
        },
        /** Drops the highlight at once, without honouring the minimum duration. */
        clear,
        /** Whether one of the given labels is lit. */
        isHeld(labels) {
            return matches(heldLabelId, labels);
        },
    };
}
