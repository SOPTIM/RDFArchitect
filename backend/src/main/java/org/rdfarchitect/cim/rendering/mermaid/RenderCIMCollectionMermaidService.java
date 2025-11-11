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

package org.rdfarchitect.cim.rendering.mermaid;

import lombok.Getter;
import org.rdfarchitect.cim.data.dto.CIMClass;
import org.rdfarchitect.cim.data.dto.CIMCollection;
import org.rdfarchitect.cim.data.dto.CIMPackage;
import org.rdfarchitect.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.cim.rendering.RenderCIMCollectionUseCase;
import org.rdfarchitect.cim.rendering.mermaid.builder.CIMAssociationToMermaidBuilder;
import org.rdfarchitect.cim.rendering.mermaid.builder.CIMAttributeToMermaidBuilder;
import org.rdfarchitect.cim.rendering.mermaid.builder.CIMClassToMermaidBuilder;
import org.rdfarchitect.cim.rendering.mermaid.builder.CIMEnumEntryToMermaidBuilder;
import org.rdfarchitect.cim.rendering.mermaid.builder.CIMPackageToMermaidBuilder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Converts a {@link CIMCollection} to a String that can be rendered as a UML diagram using the mermaid syntax.
 */
@Service
public class RenderCIMCollectionMermaidService implements RenderCIMCollectionUseCase {

    private static final String ON_CLICK_CALLBACK_FUNCTION_NAME = "getClassInformation";

    private CIMCollection cimCollection;

    private StringBuilder mermaidString;

    private Map<String, UUID> uriToUUIDMap;

    private static final String TAB = "    ";

    @Getter
    public enum MermaidThemeConfig {
        THEME("base"),
        FONT_FAMILY("sans-serif"),
        TEXT_COLOR("#303030"),
        PRIMARY_COLOR("#e0e0e0"),
        PRIMARY_BORDER_COLOR("#303030"),
        LINE_COLOR("#303030");

        private final String value;

        MermaidThemeConfig(String value) {
            this.value = value;
        }
    }

    @Override
    public String renderUML(CIMCollection cimCollection) {
        //setup
        this.cimCollection = cimCollection;
        this.mermaidString = new StringBuilder();
        createUUIDUriPairs();

        //mermaid config
        appendConfig();
        mermaidString.append("classDiagram\n");

        //actual mermaid String generation
        appendPackages();

        appendClassInheritance();

        appendAssociations();

        appendOnClickFunctionality();
        return mermaidString.toString();
    }

    /**
     * Assigns a {@link UUID} to each uri in the cimCollection.
     * The UUIDs are used as ids for each object in the mermaid String since uris can contain chars that are not allowed in the mermaid syntax
     */
    private void createUUIDUriPairs() {
        uriToUUIDMap = new HashMap<>();

        cimCollection.getPackages().forEach(value -> uriToUUIDMap.put(value.getUri().toString(), value.getUuid()));

        cimCollection.getClasses().forEach(value -> uriToUUIDMap.put(value.getUri().toString(), value.getUuid()));

        cimCollection.getAttributes().forEach(value -> uriToUUIDMap.put(value.getUri().toString(), value.getUuid()));

        cimCollection.getAssociations().forEach(value -> uriToUUIDMap.put(value.getUri().toString(), value.getUuid()));

        cimCollection.getEnums().forEach(value -> uriToUUIDMap.put(value.getUri().toString(), value.getUuid()));

        cimCollection.getEnumEntries().forEach(value -> uriToUUIDMap.put(value.getUri().toString(), value.getUuid()));
    }

    private void appendConfig() {
        mermaidString
                  .append("---\n")
                  .append("config:\n")
                  .append(TAB)
                  .append("theme: ").append(MermaidThemeConfig.THEME.getValue()).append("\n")
                  .append(TAB)
                  .append("themeVariables:\n")
                  .append(TAB)
                  .append(TAB)
                  .append("fontFamily: \"").append(MermaidThemeConfig.FONT_FAMILY.getValue()).append("\"\n")
                  .append(TAB)
                  .append(TAB)
                  .append("textColor: \"").append(MermaidThemeConfig.TEXT_COLOR.getValue()).append("\"\n")
                  .append(TAB)
                  .append(TAB)
                  .append("primaryColor: \"").append(MermaidThemeConfig.PRIMARY_COLOR.getValue()).append("\"\n")
                  .append(TAB)
                  .append(TAB)
                  .append("primaryBorderColor: \"").append(MermaidThemeConfig.PRIMARY_BORDER_COLOR.getValue()).append("\"\n")
                  .append(TAB)
                  .append(TAB)
                  .append("lineColor: \"").append(MermaidThemeConfig.LINE_COLOR.getValue()).append("\"\n")
                  .append("---\n");
    }

    /**
     * Appends all packages to the mermaid String
     */
    private void appendPackages() {
        for (var cimPackage : cimCollection.getPackages()) {
            var packageContents = getClassMermaidStrings(cimPackage);
            packageContents.addAll(getEnumMermaidStrings(cimPackage));
            mermaidString.append(
                      new CIMPackageToMermaidBuilder(cimPackage, packageContents).build()
                                );
        }
        var varNotInPackageContents = getClassMermaidStrings(null);
        varNotInPackageContents.addAll(getEnumMermaidStrings(null));
        mermaidString.append(
                  new CIMPackageToMermaidBuilder(null, varNotInPackageContents).build()
                            );
    }

    /**
     * Generates the mermaid Strings for all classes in a package
     *
     * @param cimPackage The package to generate the classes for
     *
     * @return A list of mermaid Strings for the classes in the package
     */
    private List<StringBuilder> getClassMermaidStrings(CIMPackage cimPackage) {
        var classMermaidStrings = new ArrayList<StringBuilder>();
        for (var cimClass : cimCollection.getClasses()) {
            if (!classIsInPackage(cimClass, cimPackage)) {
                continue;
            }
            classMermaidStrings.add(
                      new CIMClassToMermaidBuilder(cimClass, uriToUUIDMap.get(cimClass.getUri().toString()))
                                .appendClassContents(getAttributeMermaidStrings(cimClass))
                                .build()
                                   );
        }
        return classMermaidStrings;
    }

    private List<StringBuilder> getEnumMermaidStrings(CIMPackage cimPackage) {
        var enumMermaidStrings = new ArrayList<StringBuilder>();
        for (var cimEnumClass : cimCollection.getEnums()) {
            if (!classIsInPackage(cimEnumClass, cimPackage)) {
                continue;
            }
            enumMermaidStrings.add(
                      new CIMClassToMermaidBuilder(cimEnumClass, uriToUUIDMap.get(cimEnumClass.getUri().toString()))
                                .appendClassContents(getEnumEntryMermaidStrings(cimEnumClass))
                                .build()
                                  );
        }
        return enumMermaidStrings;
    }

    private List<StringBuilder> getEnumEntryMermaidStrings(CIMClass cimEnumClass) {
        var enumEntryMermaidStrings = new ArrayList<StringBuilder>();
        for (var cimEnumEntry : cimCollection.getEnumEntries()) {
            if (!cimEnumEntry.getType().getUri().equals(cimEnumClass.getUri())) {
                continue;
            }
            enumEntryMermaidStrings.add(
                      new CIMEnumEntryToMermaidBuilder(cimEnumEntry).build()
                                       );
        }
        return enumEntryMermaidStrings;
    }

    /**
     * Generates the mermaid Strings for all attributes in a class.
     *
     * @param cimClass The class to generate the attributes for.
     *
     * @return A list of mermaid Strings for the attributes in the class.
     */
    private List<StringBuilder> getAttributeMermaidStrings(CIMClass cimClass) {
        var attributeMermaidStrings = new ArrayList<StringBuilder>();
        for (var cimAttribute : cimCollection.getAttributes()) {
            if (!cimAttribute.getDomain().getUri().equals(cimClass.getUri())) {
                continue;
            }
            attributeMermaidStrings.add(
                      new CIMAttributeToMermaidBuilder(cimAttribute).build()
                                       );
        }
        return attributeMermaidStrings;
    }

    private void appendClassInheritance() {
        for (var cimClass : cimCollection.getClasses()) {
            if (cimClass.getSuperClass() == null) {
                continue;
            }
            var classUUID = uriToUUIDMap.get(cimClass.getUri().toString());
            var superClassUUID = uriToUUIDMap.get(cimClass.getSuperClass().getUri().toString());
            mermaidString
                      .append(TAB)
                      .append("`")
                      .append(classUUID)
                      .append("`")
                      .append(" --|> ")
                      .append("`")
                      .append(superClassUUID)
                      .append("`")
                      .append("\n");
        }
    }

    private void appendAssociations() {
        var handledAssociations = new ArrayList<URI>();
        for (var from : cimCollection.getAssociations()) {
            var to = cimCollection.getAssociations().stream()
                                  .filter(possibleTo -> from.getInverseRoleName().getUri().equals(possibleTo.getUri()))
                                  .findFirst()
                                  .orElse(null);
            if (to == null || (handledAssociations.contains(from.getUri()) && handledAssociations.contains(to.getUri()))) {
                continue;
            }

            mermaidString
                      .append(TAB)
                      .append(new CIMAssociationToMermaidBuilder(from, to, uriToUUIDMap).build());
            handledAssociations.add(from.getUri());
            handledAssociations.add(to.getUri());
        }
    }

    /**
     * Generates the line for the mermaid String that allows the user to click on the class and call a function.
     */
    private void appendOnClickFunctionality() {
        var clickableEntitylist = new ArrayList<>(cimCollection.getClasses());
        clickableEntitylist.addAll(cimCollection.getEnums());
        clickableEntitylist.forEach(cimEntity -> {
            var uuid = uriToUUIDMap.get(cimEntity.getUri().toString());
            mermaidString
                      .append(TAB)
                      .append("click `")
                      .append(uuid)
                      .append("` call ")
                      .append(ON_CLICK_CALLBACK_FUNCTION_NAME)
                      .append("(\"")
                      .append(cimEntity.getUuid().toString())
                      .append("\")")
                      .append("\n");
        });
    }

    private boolean classIsInPackage(CIMClass cimClass, CIMPackage cimPackage) {
        if (cimClass.getBelongsToCategory() == null) {
            return cimPackage == null;
        }
        if (cimPackage == null) {
            return false;
        }
        return cimClass.getBelongsToCategory().getUri().equals(cimPackage.getUri());
    }
}
