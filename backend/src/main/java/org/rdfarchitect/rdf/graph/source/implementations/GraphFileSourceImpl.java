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

package org.rdfarchitect.rdf.graph.source.implementations;

import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.shared.JenaException;
import org.rdfarchitect.exception.database.DataAccessException;
import org.rdfarchitect.rdf.graph.source.GraphSource;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileNotFoundException;
import java.io.IOException;

public class GraphFileSourceImpl implements GraphSource {

    private final MultipartFile file;
    private Graph graph = null;
    private final String graphName;
    private static final String SOURCE_TYPE = "file";

    public GraphFileSourceImpl(MultipartFile file, String graphName) {
        this.file = file;
        this.graphName = graphName;
    }

    @Override
    public Graph graph() throws DataAccessException {
        if (graph != null) {
            return graph;
        }

        String fileName = file.getOriginalFilename();

        try {
            Model model = ModelFactory.createDefaultModel();
            Lang lang = RDFLanguages.filenameToLang(fileName);
            if(lang == null) {
                model.read(file.getInputStream(), null);
            }
            else{
                model.read(file.getInputStream(), null, lang.getName());
            }
            graph = model.getGraph();
            if (graph == null) {
                throw new DataAccessException("Unable to get graph from file " + fileName + ".");
            }
        } catch (FileNotFoundException _) {
            throw new DataAccessException("Unable to get graph from file " + fileName + " , because the file does not exist.");
        } catch (IOException _) {
            throw new DataAccessException("Unable to get graph from file " + fileName + " , because an IO operation failed.");
        } catch (JenaException _) {
            throw new DataAccessException("Unable to get graph from file " + fileName + ".");
        }
        return graph;
    }

    @Override
    public String graphName() {
        return graphName;
    }

    @Override
    public String getGraphSourceType() {
        return SOURCE_TYPE;
    }
}
