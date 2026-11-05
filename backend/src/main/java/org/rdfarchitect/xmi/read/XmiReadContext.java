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

package org.rdfarchitect.xmi.read;

import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What the parts of a file have in common: the index they fill between them, and the lookups they
 * join over.
 *
 * <p>The model part creates the attributes and the enum literals and is the only one that knows
 * which id each got. The extension part and the stereotype applications address them by that id and
 * nothing else, which is what these maps are for. Everything else a part needs while reading is its
 * own and stays there.
 */
@Getter
final class XmiReadContext {

    private final XmiIndex index = new XmiIndex();

    private final Map<String, XmiAttribute> attributesById = new HashMap<>();

    private final Map<String, XmiEnumLiteral> literalsById = new HashMap<>();

    /**
     * Both halves a connector produced, by the connector's id. A stereotype is applied to the
     * association as a whole and arrives after the extension has been read, so it has to find the
     * two halves that association became.
     */
    private final Map<String, List<XmiAssociationEnd>> endsByConnectorId = new HashMap<>();

    /** The prefix table while it is being read; its halves are paired once the file is done. */
    private final XmiPrefixTable prefixTable = new XmiPrefixTable();

    /** Records a half under the connector it came from. */
    void rememberEnd(XmiAssociationEnd end) {
        index.getAssociationEnds().add(end);
        endsByConnectorId.computeIfAbsent(end.getConnectorId(), _ -> new ArrayList<>()).add(end);
    }
}
