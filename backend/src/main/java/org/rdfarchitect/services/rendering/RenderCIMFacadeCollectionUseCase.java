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

package org.rdfarchitect.services.rendering;

import org.rdfarchitect.api.dto.dl.RenderingLayoutData;
import org.rdfarchitect.api.dto.rendering.RenderingDataDTO;
import org.rdfarchitect.models.cim.data.dto.facade.ICIMModelFacade;
import org.rdfarchitect.models.cim.rendering.GraphFilter;

import java.util.List;

/**
 * Converts a {@link ICIMModelFacade} to a DTO that contains data required to render a UML diagram.
 */
public interface RenderCIMFacadeCollectionUseCase {

    /**
     * Generates the rendering data for a CIM model using pre-fetched layout data.
     *
     * @param cimModel the CIM model facade to read the diagram content from
     * @param filter filter deciding which parts of the model are rendered
     * @param layoutData pre-fetched diagram layout data (may be null)
     * @return a dto that contains all data required to render a UML diagram for the given model
     */
    default RenderingDataDTO renderUML(
            ICIMModelFacade cimModel, GraphFilter filter, RenderingLayoutData layoutData) {
        return renderUML(cimModel, filter, layoutData, List.of(), null);
    }

    /**
     * Generates the rendering data for a CIM model, optionally enriching each rendered class with
     * the matching class's properties from other profiles of the same dataset.
     *
     * @param cimModel the CIM model facade to read the diagram content from
     * @param filter filter deciding which parts of the model are rendered
     * @param layoutData pre-fetched diagram layout data (may be null)
     * @param otherProfiles the other profiles whose matching classes contribute properties when
     *     {@link GraphFilter#isIncludePropertiesFromOtherProfiles()} is set
     * @param primaryColor the cross-profile color of the rendered graph, used to colorize its own
     *     properties when other-profile properties are merged in (may be null)
     * @return a dto that contains all data required to render a UML diagram for the given model
     */
    RenderingDataDTO renderUML(
            ICIMModelFacade cimModel,
            GraphFilter filter,
            RenderingLayoutData layoutData,
            List<CIMProfileModel> otherProfiles,
            String primaryColor);

    /**
     * Generates the rendering data for a cross-profile (merged) diagram: all classes across the
     * given profiles are merged by class URI into a single node per class, with properties grouped
     * by their source profile.
     *
     * @param profiles all profiles of the dataset, each contributing its color
     * @param layoutData pre-fetched cross-profile diagram layout data (may be null)
     * @return a dto that contains all data required to render the merged UML diagram
     */
    RenderingDataDTO renderMergedUML(
            List<CIMProfileModel> profiles, RenderingLayoutData layoutData);
}
