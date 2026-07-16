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

package org.rdfarchitect.services.rendering.svelteflow;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.jena.rdf.model.Model;
import org.rdfarchitect.api.dto.dl.RenderingLayoutData;
import org.rdfarchitect.api.dto.rendering.RenderingDataDTO;
import org.rdfarchitect.api.dto.rendering.svelteflow.SvelteFlowDTO;
import org.rdfarchitect.api.dto.rendering.svelteflow.sub.AttributeDTO;
import org.rdfarchitect.api.dto.rendering.svelteflow.sub.EdgeDTO;
import org.rdfarchitect.api.dto.rendering.svelteflow.sub.EdgeDataDTO;
import org.rdfarchitect.api.dto.rendering.svelteflow.sub.EnumEntryDTO;
import org.rdfarchitect.api.dto.rendering.svelteflow.sub.NodeDTO;
import org.rdfarchitect.api.dto.rendering.svelteflow.sub.NodeDataDTO;
import org.rdfarchitect.api.dto.rendering.svelteflow.sub.PositionDTO;
import org.rdfarchitect.models.cim.data.dto.CIMAssociation;
import org.rdfarchitect.models.cim.data.dto.CIMClass;
import org.rdfarchitect.models.cim.data.dto.CIMCollection;
import org.rdfarchitect.models.cim.data.dto.facade.CIMModelFacade;
import org.rdfarchitect.models.cim.data.dto.facade.DefaultCIMClassCategory;
import org.rdfarchitect.models.cim.data.dto.facade.ICIMClass;
import org.rdfarchitect.models.cim.data.dto.facade.ICIMClassCategory;
import org.rdfarchitect.models.cim.data.dto.facade.ICIMModelFacade;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSAssociationUsed;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSMultiplicity;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSStereotype;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.models.cim.rendering.GraphFilter;
import org.rdfarchitect.models.cim.rendering.RenderingUtils;
import org.rdfarchitect.services.dl.select.FetchRenderingLayoutDataUseCase;
import org.rdfarchitect.services.rendering.DiagramToCIMCollectionConverterUseCase;
import org.rdfarchitect.services.rendering.RenderCIMCollectionUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

/**
 * Converts a {@link CIMCollection} to a DTO Record that contains two JSON arrays with nodes and
 * edges used to render a UML diagram using the JavaScript library SvelteFlow.
 */
@RequiredArgsConstructor
@Service
@ConditionalOnProperty(
        name = "rendering.renderer",
        havingValue = "svelteflow",
        matchIfMissing = true)
public class RenderCIMFacadeCollectionSvelteFlowService {

    private final FetchRenderingLayoutDataUseCase fetchRenderingLayoutDataUseCase;

    private final DiagramToCIMCollectionConverterUseCase converter;

    // CONSTANTS FOR SVELTEFLOW CUSTOM NODE/EDGE TYPES
    private static final String CLASS_NODE_TYPE = "class";
    private static final String INHERITANCE_EDGE_TYPE = "inheritance";
    private static final String ASSOCIATION_EDGE_TYPE = "association";

    public RenderingDataDTO renderUML(ICIMModelFacade cimModel, GraphFilter filter, RenderingLayoutData layoutData) {
        var pack = cimModel.getCIMClassCategory(filter.getPackageUUID() == null ? null : UUID.fromString(filter.getPackageUUID()));
        var classes = pack.getClasses();

        return null;
    }
}
