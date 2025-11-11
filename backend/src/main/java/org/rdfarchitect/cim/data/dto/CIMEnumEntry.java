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

package org.rdfarchitect.cim.data.dto;

import lombok.Builder;
import lombok.Data;
import org.rdfarchitect.cim.data.dto.relations.CIMSStereotype;
import org.rdfarchitect.cim.data.dto.relations.RDFSComment;
import org.rdfarchitect.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.cim.data.dto.relations.RDFType;
import org.rdfarchitect.cim.data.dto.relations.uri.URI;

import java.util.UUID;

@Data
@Builder(toBuilder = true)
public class CIMEnumEntry {

    private UUID uuid;

    private URI uri;

    private RDFType type;

    private RDFSLabel label;

    private RDFSComment comment;

    private CIMSStereotype stereotype;

    public String getEnumClassString() {
        return uri.getSuffix().split("\\.", 2)[0];
    }

    public URI getEnumClassURI() {
        return new URI(uri.getPrefix() + this.getEnumClassString());
    }
}
