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

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;

public class RDFUtils {

    private RDFUtils() {
    }

    /**
     * Prepares a string for usage in a SPARQL query
     *
     * @param s String to be wrapped
     *
     * @return Wrapped string
     */
    public static String wrapURLorLiteral(String s) {
        if (s == null) {
            throw new NullPointerException("URI or Literal cannot be null");
        }
        try {
            new URI(s).toURL();
            return "<" + s + ">";
        } catch (URISyntaxException | MalformedURLException | IllegalArgumentException _) {
            return "\"" + s + "\"";
        }
    }

    /**
     * Checks if given String is an URL
     *
     * @param s Possible URL
     *
     * @return true if URL otherwise false
     */
    public static boolean isURL(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        try {
            new URI(s).toURL();
            return true;
        } catch (URISyntaxException | MalformedURLException | IllegalArgumentException _) {
            return false;
        }
    }
}
