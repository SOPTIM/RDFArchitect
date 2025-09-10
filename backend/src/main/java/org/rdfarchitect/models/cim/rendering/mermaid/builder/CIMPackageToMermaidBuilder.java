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

package org.rdfarchitect.models.cim.rendering.mermaid.builder;

import org.rdfarchitect.models.cim.search.data.dto.CIMPackage;

import java.util.List;

public class CIMPackageToMermaidBuilder {

    private final CIMPackage cimPackage;

    List<StringBuilder> packageContents;

    private static final String TAB = "    ";

    public CIMPackageToMermaidBuilder(CIMPackage cimPackage, List<StringBuilder> packageContents) {
        this.cimPackage = cimPackage;
        this.packageContents = packageContents;
    }

    public StringBuilder build() {
        if (packageContents.isEmpty()) {
            return new StringBuilder();
        }
        var packageMermaidString = new StringBuilder();
        if (cimPackage != null) {
            packageMermaidString
                      .append(TAB)
                      .append("namespace ")
                      .append(cimPackage.getLabel().getValue().replaceAll("\\W", "_"))
                      .append("{\n");
        }

        packageContents.forEach(packageContent -> {
                                    var lines = packageContent.toString().split("\n");
                                    for (var line : lines) {
                                        if (cimPackage != null) {
                                            packageMermaidString
                                                      .append(TAB);
                                        }
                                        packageMermaidString
                                                  .append(TAB)
                                                  .append(line)
                                                  .append("\n");
                                    }
                                }
                               );
        if (cimPackage != null) {
            packageMermaidString
                      .append(TAB)
                      .append("}")
                      .append("\n");
        }
        return packageMermaidString;
    }
}
