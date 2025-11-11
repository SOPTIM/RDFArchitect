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

package org.rdfarchitect.services.shacl;

import lombok.RequiredArgsConstructor;
import org.apache.jena.query.TxnType;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.system.PrefixEntry;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.rdf.graph.wrapper.GraphRewindable;
import org.rdfarchitect.shacl.generator.SHACLFromCIMGenerator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@RequiredArgsConstructor
public class SHACLGenerateService implements SHACLGenerateUseCase {

    private final DatabasePort databasePort;

    @Override
    public String exportGeneratedSHACLGraph(GraphIdentifier graphIdentifier, PrefixEntry shaclPrefix) {
        GraphRewindable ontologyGraph = null;
        var prefixes = databasePort.getPrefixMapping(graphIdentifier.getDatasetName());
        try (var outStream = new ByteArrayOutputStream()) {
            ontologyGraph = databasePort.getGraph(graphIdentifier);
            ontologyGraph.begin(TxnType.READ);
            var ontologyModel = ModelFactory.createModelForGraph(ontologyGraph);
            ontologyModel.setNsPrefixes(prefixes);
            var shaclModel = new SHACLFromCIMGenerator(ontologyModel, shaclPrefix, true).generate();
            shaclModel.write(outStream, Lang.TTL.getName());
            return outStream.toString();
        } catch (IOException e) {
            throw new RuntimeException("Error while writing SHACL model to output stream", e);
        } finally {
            if (ontologyGraph != null) {
                ontologyGraph.end();
            }
        }
    }
}
