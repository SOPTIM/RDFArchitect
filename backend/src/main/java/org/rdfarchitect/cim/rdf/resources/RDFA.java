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

package org.rdfarchitect.cim.rdf.resources;

import lombok.experimental.UtilityClass;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.ResourceFactory;

@UtilityClass
public class RDFA {

    public final String URI = "https://rdf-architect.soptim.de#";

    public final Property uuid = ResourceFactory.createProperty("http://example.org#uuid");

    public final String NS_PREFIX = "rdfa";

    public final String NS_PREFIX_SHACL = "rdfash";
    public final String NS_URI_SHACL = "http://example.com/shacl#";

    public final String GRAPH_URI = "http://graph#";
}
