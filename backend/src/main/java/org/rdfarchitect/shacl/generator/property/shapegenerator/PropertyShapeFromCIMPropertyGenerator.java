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

package org.rdfarchitect.shacl.generator.property.shapegenerator;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.system.PrefixEntry;

public interface PropertyShapeFromCIMPropertyGenerator {

    PropertyShapeFromCIMPropertyGenerator setOntologyModel(Model ontology);

    PropertyShapeFromCIMPropertyGenerator setShaclModel(Model shacl);

    PropertyShapeFromCIMPropertyGenerator setShaclPrefix(PrefixEntry shaclPrefix);

    /**
     * Creates a property shape for a specified property. Returns null if no shape is created.
     * @param property the property to create the property shape for
     * @return a {@link Resource} representing the property shape, or null if no shape is created
     */
    Resource createPropertyShape(Resource property);
}
