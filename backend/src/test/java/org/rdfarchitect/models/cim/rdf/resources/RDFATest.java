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

package org.rdfarchitect.models.cim.rdf.resources;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The names the frontend builds graph URIs from too.
 *
 * <p>No contract carries them: the frontend names an imported schema before the backend ever sees
 * it, so the literal is written out on both sides. Pinned here and in {@code graphUri.test.js} so
 * that changing one side alone fails rather than producing schemas nothing can find.
 */
class RDFATest {

    @Test
    void graphUri_isTheNamespaceTheFrontendNamesImportedSchemasUnder() {
        assertThat(RDFA.GRAPH_URI).isEqualTo("http://graph#");
    }
}
