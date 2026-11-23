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

package org.rdfarchitect.rdf;

import lombok.experimental.UtilityClass;

import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFLanguages;

/**
 * Picks the RDF syntax of a file by its extension. {@link RDFLanguages#filenameToLang} reads its
 * argument as a URI and cuts off everything from a {@code #} on, so it finds no syntax for a file
 * such as {@code EQ#2.ttl}; a file name is no URI, so only its extension counts here.
 */
@UtilityClass
public class RDFFileLanguages {

    /**
     * Returns the RDF syntax the extension of {@code fileName} names.
     *
     * @param fileName the name of the file, possibly with a path in front
     * @return the syntax, or {@code null} when the file has no extension or one no syntax uses
     */
    public static Lang of(String fileName) {
        if (fileName == null) {
            return null;
        }
        var lastSeparator = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        var lastDot = fileName.lastIndexOf('.');
        if (lastDot <= lastSeparator || lastDot == fileName.length() - 1) {
            return null;
        }
        return RDFLanguages.fileExtToLang(fileName.substring(lastDot + 1));
    }
}
