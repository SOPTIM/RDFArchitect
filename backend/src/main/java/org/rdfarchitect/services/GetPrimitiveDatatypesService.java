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

package org.rdfarchitect.services;

import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.rdfarchitect.cim.data.dto.relations.uri.URI;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Service
public class GetPrimitiveDatatypesService implements GetPrimitiveDatatypesUseCase {

    private List<URI> xsdURIList;

    @Override
    public List<URI> getPrimitiveDatatypes() {
        if (xsdURIList == null) {
            TypeMapper tm = new TypeMapper();
            XSDDatatype.loadXSDSimpleTypes(tm);
            xsdURIList = new ArrayList<>();
            for (Iterator<RDFDatatype> it = tm.listTypes(); it.hasNext(); ) {
                RDFDatatype type = it.next();
                xsdURIList.add(new URI(type.getURI()));
            }
        }
        return xsdURIList;
    }
}
