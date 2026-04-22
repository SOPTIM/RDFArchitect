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

import lombok.RequiredArgsConstructor;

import org.apache.jena.query.QuerySolution;
import org.rdfarchitect.dl.data.DLUtils;
import org.rdfarchitect.dl.data.dto.relations.MRID;
import org.rdfarchitect.dl.data.dto.relations.XYPosition;

/**
 * Parses a {@link QuerySolution} to extract the values of the variables used in the context of
 * CGMES DiagramLayout Profile.
 */
@RequiredArgsConstructor
public class DLQuerySolutionParser {

    private final QuerySolution qs;

    /**
     * Extracts a {@link MRID} from the query solution.
     *
     * @param mridVar The variable name of the MRID to be extracted.
     * @return The MRID or null, if the given variables doesn't exist in the solution.
     */
    public MRID getMRID(String mridVar) {
        if (!qs.contains(mridVar)) {
            return null;
        }
        var mRID = qs.getResource(mridVar).getURI();
        var uuid = DLUtils.extractUUIDFromMRID(mRID);
        return new MRID(uuid);
    }

    /**
     * Extracts a name from the query solution.
     *
     * @param nameVar The variable name of the name to be extracted.
     * @return The name or null, if the given variables doesn't exist in the solution.
     */
    public String getName(String nameVar) {
        if (!qs.contains(nameVar)) {
            return null;
        }
        return qs.getLiteral(nameVar).getString();
    }

    /**
     * Extracts the {@link XYPosition} from the query solution.
     *
     * @return The XYPosition or null, if the given variables doesn't exist in the solution.
     */
    public XYPosition getXYZPosition() {
        if (!qs.contains(DLQueryVars.X_POSITION) || !qs.contains(DLQueryVars.Y_POSITION)) {
            return null;
        }
        var xPosition = qs.getLiteral(DLQueryVars.X_POSITION).getFloat();
        var yPosition = qs.getLiteral(DLQueryVars.Y_POSITION).getFloat();
        var zPositionLiteral = qs.getLiteral(DLQueryVars.Z_POSITION);
        var zPosition = 0;
        if(zPositionLiteral != null){
            zPosition = zPositionLiteral.getInt();
        }
        return new XYPosition(xPosition, yPosition, zPosition);
    }
}
