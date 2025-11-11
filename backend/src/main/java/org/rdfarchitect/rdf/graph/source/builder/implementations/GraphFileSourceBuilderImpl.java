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

package org.rdfarchitect.rdf.graph.source.builder.implementations;

import org.rdfarchitect.exception.database.DataAccessException;
import org.rdfarchitect.rdf.graph.source.GraphSource;
import org.rdfarchitect.rdf.graph.source.builder.GraphSourceBuilder;
import org.rdfarchitect.rdf.graph.source.implementations.GraphFileSourceImpl;
import org.springframework.web.multipart.MultipartFile;

public class GraphFileSourceBuilderImpl implements GraphSourceBuilder {

    String graphName;
    MultipartFile file;

    public GraphFileSourceBuilderImpl() {
        this.graphName = null;
        file = null;
    }

    public GraphSourceBuilder setFile(MultipartFile file) {
        this.file = file;
        return this;
    }

    @Override
    public GraphSourceBuilder setGraphName(String graphName) {
        this.graphName = graphName;
        return this;
    }

    @Override
    public GraphSource build() throws DataAccessException {
        if (file == null) {
            throw new DataAccessException("Trying to build a GraphSource with a null file!");
        }
        return new GraphFileSourceImpl(file, graphName);
    }
}
