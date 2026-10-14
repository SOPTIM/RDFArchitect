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

import java.util.HashMap;
import java.util.Map;

/**
 * What the three parts of a file have in common: the index they fill between them, and the two
 * lookups they join over.
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
}
