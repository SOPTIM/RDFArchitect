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

package org.rdfarchitect.services.update.classes;

import org.rdfarchitect.api.dto.packages.PackageDTO;

/**
 * Options shared by all classes of a single copy/paste operation: the package the copies are placed
 * in and which parts of each source class are carried over.
 *
 * @param targetPackage The package the new classes are added to, or {@code null} for the default
 *     package.
 * @param copyAsAbstract Whether the copies should be made abstract (dropping the concrete
 *     stereotype and the super class).
 * @param copyAttributes Whether the attributes of the source classes should be copied.
 * @param copyAssociations Whether the associations of the source classes should be copied.
 */
public record CopyClassOptions(
        PackageDTO targetPackage,
        boolean copyAsAbstract,
        boolean copyAttributes,
        boolean copyAssociations) {}
