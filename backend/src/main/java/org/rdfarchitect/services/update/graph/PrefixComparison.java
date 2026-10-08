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

package org.rdfarchitect.services.update.graph;

import java.util.List;

/**
 * What one prefix stands for on either side of an import: in the dataset, in the files being
 * imported, or in both. A prefix that more than one namespace lays claim to is {@code contested}
 * and has to be decided on, because storing the import as it stands would rebind it and leave the
 * classes addressed through it without a prefix.
 *
 * @param prefix the prefix, with a trailing colon (e.g. {@code cim:})
 * @param workspace what the dataset binds the prefix to, or {@code null} if it does not
 * @param imported what the files of the import bind it to, one entry per distinct namespace
 * @param contested whether the bindings disagree
 */
public record PrefixComparison(
        String prefix, PrefixBinding workspace, List<PrefixBinding> imported, boolean contested) {}
