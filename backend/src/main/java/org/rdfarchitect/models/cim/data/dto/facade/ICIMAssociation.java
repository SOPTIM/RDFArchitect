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

package org.rdfarchitect.models.cim.data.dto.facade;

import org.rdfarchitect.models.cim.data.dto.relations.CIMSAssociationUsed;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSMultiplicity;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSLabel;

public interface ICIMAssociation extends ICIMResource {

    CIMSMultiplicity getMultiplicity();

    ICIMClass getDomain();

    ICIMClass getRange();

    ICIMAssociation getInverseAssociation();

    /**
     * Whether the association and its inverse carry the properties required to render an edge, i.e.
     * whether {@link #getRange()}, {@link #getInverseAssociation()}, {@link #getMultiplicity()} and
     * {@link #getAssociationUsed()} resolve on both ends instead of throwing.
     */
    boolean isRenderable();

    CIMSAssociationUsed getAssociationUsed();

    /**
     * The label of this association end, or null when the model holds none. Unlike {@link
     * #getLabel()} this does not throw, so an association without a label still renders, just
     * without its label.
     *
     * @return the label of this association end, or null
     */
    RDFSLabel getLabelOrNull();
}
