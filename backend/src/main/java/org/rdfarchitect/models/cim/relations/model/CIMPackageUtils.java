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

package org.rdfarchitect.models.cim.relations.model;

import lombok.experimental.UtilityClass;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;

import java.util.List;
import java.util.UUID;

@UtilityClass
public class CIMPackageUtils {

    /**
     * Lists all classes contained in a given package.
     *
     * @param model the RDF model
     * @param packageUuid the UUID of the package
     * @return a list of class resources belonging to the package
     * @throws IllegalStateException if the resource with the given UUID is not a package
     */
    public List<Resource> listClassesInPackage(Model model, UUID packageUuid) {
        var packageResource = CIMResourceTypeIdentifyingUtils.findUniqueSubject(model, packageUuid);
        if (!packageResource.hasProperty(RDF.type, CIMS.classCategory)) {
            throw new IllegalStateException(
                    "Resource with UUID " + packageUuid + " is not a package.");
        }
        return model.listSubjectsWithProperty(CIMS.belongsToCategory, packageResource)
                .filterKeep(cls -> cls.hasProperty(RDF.type, RDFS.Class))
                .toList();
    }
}
