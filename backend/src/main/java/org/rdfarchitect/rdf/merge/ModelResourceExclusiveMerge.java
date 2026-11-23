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

package org.rdfarchitect.rdf.merge;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.rdfarchitect.rdf.graph.GraphUtils;

import java.util.Set;

public class ModelResourceExclusiveMerge implements ModelMerger {

    /**
     * Merges two RDF models into one, ensuring that if a ressource exists in both models, only the
     * triples from the primary model are retained. Even if the secondary model contains different
     * triples for the same resource.
     *
     * @param primary the Model to merge from, which takes precedence
     * @param secondary the Model to merge into, which is merged into the primary
     * @return a new Model containing the merged results
     */
    @Override
    public Model merge(Model primary, Model secondary) {
        var resultModel = ModelFactory.createModelForGraph(GraphUtils.deepCopy(primary.getGraph()));
        var primaryPrefixes = primary.getNsPrefixMap();
        secondary.getNsPrefixMap().entrySet().stream()
                .filter(entry -> !primaryPrefixes.containsKey(entry.getKey()))
                .forEach(entry -> primaryPrefixes.put(entry.getKey(), entry.getValue()));
        resultModel.setNsPrefixes(primaryPrefixes);

        // A blank subject is copied only with a named resource that leads to it.
        secondary.listSubjects().toSet().stream()
                .filter(Resource::isURIResource)
                .filter(subject -> !primary.containsResource(subject))
                .forEach(
                        subject ->
                                GraphUtils.copyWithBlankNodes(
                                        secondary, resultModel, subject, Set.of()));
        return resultModel;
    }
}
