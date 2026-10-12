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

package org.rdfarchitect.database;

import org.apache.jena.rdf.model.Model;
import org.rdfarchitect.dl.data.dto.relations.MRID;

/**
 * Where the elements of a diagram sit.
 *
 * <p>What a caller may do with a layout, as opposed to what the implementation also has to offer
 * its workspace. The implementation is a transaction participant and therefore carries {@code
 * commit}, {@code abort}, {@code undo} and {@code redo} as well; those belong to the workspace that
 * drives the transaction, and calling them from outside would cut a version that no changelog entry
 * names.
 */
public interface DiagramLayout {

    /**
     * Returns a live view of the layout. Changes to it are written into the running transaction and
     * are committed or rolled back with it.
     */
    Model getDiagramLayoutModel();

    /** Returns the package shown when no particular one was asked for. */
    MRID getDefaultPackageMRID();
}
