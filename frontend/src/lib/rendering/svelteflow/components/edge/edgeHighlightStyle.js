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
 * The base stroke width of an edge that is neither selected nor highlighted.
 */
const BASE_STROKE_WIDTH = "2px";

/** The width an edge swells to while selected or one of its labels is held. */
const HIGHLIGHT_STROKE_WIDTH = "3.5px";

/**
 * The transition an edge grows into its highlight with: quick, so a short press already reads as
 * a pulse rather than a snap.
 */
const GROW_TRANSITION =
    "transition: stroke 120ms ease-out, stroke-width 120ms ease-out;";

/**
 * The transition an edge decays out of its highlight with: slow, so the swell fades rather than
 * cutting off abruptly once a click or drag ends.
 */
const DECAY_TRANSITION =
    "transition: stroke 450ms ease-out, stroke-width 450ms ease-out;";

/**
 * The stroke style of an edge's own path, replacing the previous dashed overlay: selecting an
 * edge, or holding one of its labels, swells it and turns it blue instead. Shared by every edge
 * type so inheritance and association edges swell identically instead of drifting apart in width,
 * colour or timing.
 *
 * @param baseColor the stroke colour the edge has while neither selected nor highlighted
 * @param highlighted whether the edge is currently selected or one of its labels is held
 */
export function edgeHighlightStyle(baseColor, highlighted) {
    const width = highlighted ? HIGHLIGHT_STROKE_WIDTH : BASE_STROKE_WIDTH;
    const color = highlighted ? "var(--color-blue)" : baseColor;
    const transition = highlighted ? GROW_TRANSITION : DECAY_TRANSITION;
    return `stroke-width: ${width}; stroke: ${color}; ${transition}`;
}
