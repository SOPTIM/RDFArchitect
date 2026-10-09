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

package org.rdfarchitect.services.shacl.form;

import org.rdfarchitect.shacl.dto.ShapeEditRequest;
import org.rdfarchitect.shacl.dto.ShapeEditResult;
import org.rdfarchitect.shacl.dto.ShapesForm;

/**
 * A constraints document seen and edited as shapes rather than as Turtle.
 *
 * <p>Both operations are pure text transformations on the document the caller holds, so an editor
 * can offer a form view over its unsaved buffer and switch back to the text without anything being
 * stored in between. Saving stays the caller's single, explicit step.
 */
public interface ShapeFormUseCase {

    /** Reads Turtle into the shapes a form can show. Text that does not parse yields the error. */
    ShapesForm parse(String turtle);

    /**
     * Applies one form edit to a document's text.
     *
     * <p>Only the edited shape's statement is rewritten. Everything else keeps the bytes its author
     * gave it, which is what makes a form view safe to offer on an imported ENTSO-E file.
     */
    ShapeEditResult apply(ShapeEditRequest request);
}
