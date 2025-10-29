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

package org.rdfarchitect.dl.queries;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Class containing variables for SPARQL queries that are used in the context of the CGMES DiagramLayout Profile.
 * This is to ensure they are the same over multiple files.
 * This prohibits typos in query and results.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DLQueryVars {

    //DIAGRAM
    public static final String DIAGRAM_MRID = "?diagramMRID";
    public static final String DIAGRAM_NAME = "?diagramName";
    public static final String ORIENTATION = "?orientation";

    //DIAGRAMOBJECT
    public static final String DO_MRID = "?doMRID";
    public static final String DO_NAME = "?doName";
    public static final String IO_MRID = "?ioMRID";

    //DIAGRAMOBJECTPOINT
    public static final String DOP_MRID = "?dopMRID";
    public static final String X_POSITION = "?xPosition";
    public static final String Y_POSITION = "?yPosition";
}
