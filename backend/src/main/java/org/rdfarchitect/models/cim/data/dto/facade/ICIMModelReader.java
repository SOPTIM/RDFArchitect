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

import org.rdfarchitect.models.cim.data.dto.facade.header.ICIMProfileHeader;

import java.util.List;
import java.util.UUID;

/**
 * Reads a graph as a CIM profile: its classes, its categories and its profile header.
 *
 * <p>Named for the capability rather than for the implementation, because reading and writing a
 * profile graph are meant to end up on one object over one {@link org.apache.jena.rdf.model.Model}
 * — {@link CIMModelFacade} is that object. Keeping the two contracts apart is what lets the
 * rendering path ask for a graph it can only read, and get a guarantee rather than a convention.
 *
 * <p>Everything here is a view over the model and is computed on call, not cached, so a getter is
 * cheap to ignore and not cheap to call in a loop.
 */
public interface ICIMModelReader {

    String getGraphUri();

    List<ICIMClass> getCIMClasses();

    /**
     * Looks a single class up by the uuid it carries in this graph, without reading the other
     * classes of the graph.
     *
     * @return the class, or null if the graph holds no class under that uuid
     */
    ICIMClass getCIMClass(UUID uuid);

    List<ICIMClassCategory> getCIMClassCategories();

    ICIMClassCategory getCIMClassCategory(UUID uuid);

    /**
     * The profile header of this graph, saying which profile and which version it is.
     *
     * @return the header, never null, see {@link ICIMProfileHeader#isPresent()}
     */
    ICIMProfileHeader getHeader();
}
