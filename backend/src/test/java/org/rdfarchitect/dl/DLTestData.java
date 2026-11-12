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

package org.rdfarchitect.dl;

import lombok.experimental.UtilityClass;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.util.ResourceUtils;
import org.rdfarchitect.dl.data.dto.relations.MRID;

import java.util.HashSet;
import java.util.UUID;

@UtilityClass
public class DLTestData {

    private static final String MRID_NAMESPACE = "urn:mrid:";
    private static final String FILE =
            "src/test/java/org/rdfarchitect/dl/testdata/diagramLayout.ttl";

    public static final UUID PACKAGE_DIAGRAM = uuid("d1");
    public static final UUID CUSTOM_DIAGRAM = uuid("d2");

    public static final UUID SUB = uuid("a1");
    public static final UUID SUPER = uuid("a2");
    public static final UUID TARGET = uuid("a3");
    public static final UUID ASSOCIATION_END = uuid("a4");
    public static final UUID INVERSE_ASSOCIATION_END = uuid("a5");

    public static final MRID SUB_DO = mrid("c1");
    public static final MRID SUPER_DO = mrid("c2");
    public static final MRID TARGET_DO = mrid("c3");
    public static final MRID SUB_DO_IN_CUSTOM_DIAGRAM = mrid("c4");

    public static final MRID SUB_POINT = mrid("f1");
    public static final MRID SUPER_POINT = mrid("f2");
    public static final MRID TARGET_POINT = mrid("f3");
    public static final MRID LABEL_POINT = mrid("f4");

    public static final MRID SUB_GLUE_POINT = mrid("e1");
    public static final MRID SUPER_GLUE_POINT = mrid("e2");
    public static final MRID TARGET_GLUE_POINT = mrid("e3");

    public static final MRID INHERITANCE_DO = mrid("b1");
    public static final MRID ASSOCIATION_DO = mrid("b2");
    public static final MRID LABEL_DO = mrid("b3");
    public static final MRID INHERITANCE_SOURCE_POINT = mrid("00");
    public static final MRID INHERITANCE_BEND_POINT = mrid("02");
    public static final MRID INHERITANCE_TARGET_POINT = mrid("10");

    public static Model load() {
        var model = ModelFactory.createDefaultModel();
        RDFParser.source(FILE).lang(Lang.TURTLE).parse(model);
        var resources = new HashSet<Resource>();
        model.listStatements()
                .forEachRemaining(
                        statement -> {
                            resources.add(statement.getSubject());
                            if (statement.getObject().isURIResource()) {
                                resources.add(statement.getResource());
                            }
                        });
        for (var resource : resources) {
            if (resource.getURI().startsWith(MRID_NAMESPACE)) {
                ResourceUtils.renameResource(
                        resource, resource.getURI().substring(MRID_NAMESPACE.length()));
            }
        }
        return model;
    }

    private static UUID uuid(String suffix) {
        return UUID.fromString("00000000-0000-4000-8000-0000000000" + suffix);
    }

    private static MRID mrid(String suffix) {
        return new MRID(uuid(suffix));
    }
}
