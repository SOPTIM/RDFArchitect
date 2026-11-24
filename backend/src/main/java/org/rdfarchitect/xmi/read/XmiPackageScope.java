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

/**
 * How much of a package a schema is made of.
 *
 * <p>Both are closed over attribute types and superclasses either way; what differs is where the
 * closure starts.
 */
public enum XmiPackageScope {

    /**
     * The package and everything below it. What a profile is: a release ships one file per profile
     * and its sub-packages are part of it.
     */
    WITH_SUB_PACKAGES,

    /**
     * Only the classes the package declares itself, leaving its sub-packages to be chosen on their
     * own.
     *
     * <p>Without this a container package could only be taken whole or not at all, and the classes
     * it declares beside its sub-packages would be unreachable: 162 of the CGMES 3.0 model's sit
     * like that, 39 in {@code Environmental} alone.
     */
    PACKAGE_ONLY
}
