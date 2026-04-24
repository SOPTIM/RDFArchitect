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

package org.rdfarchitect.models.cim.rendering.mermaid;

import lombok.Getter;

import org.rdfarchitect.api.dto.rendering.RenderingDataDTO;
import org.rdfarchitect.api.dto.rendering.mermaid.MermaidDTO;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.cim.data.dto.CIMClass;
import org.rdfarchitect.models.cim.data.dto.CIMCollection;
import org.rdfarchitect.models.cim.data.dto.CIMPackage;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.models.cim.rendering.RenderCIMCollectionUseCase;
import org.rdfarchitect.models.cim.rendering.RenderingUtils;
import org.rdfarchitect.models.cim.rendering.mermaid.builder.CIMAssociationToMermaidBuilder;
import org.rdfarchitect.models.cim.rendering.mermaid.builder.CIMAttributeToMermaidBuilder;
import org.rdfarchitect.models.cim.rendering.mermaid.builder.CIMClassToMermaidBuilder;
import org.rdfarchitect.models.cim.rendering.mermaid.builder.CIMEnumEntryToMermaidBuilder;
import org.rdfarchitect.models.cim.rendering.mermaid.builder.CIMPackageToMermaidBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Converts a {@link CIMCollection} to a String that can be rendered as a UML diagram using the
 * mermaid syntax.
 */
public class RenderCIMCollectionMermaidService implements RenderCIMCollectionUseCase {

    @Override
    public RenderingDataDTO renderUML(
            CIMCollection cimCollection, GraphIdentifier graphIdentifier, UUID packageUUID) {
        if (!RenderingUtils.hasRenderableClasses(cimCollection)) {
            return null;
        }

        // setup
        var renderContext =
                new RenderContext(
                        cimCollection,
                        RenderingUtils.createUUIDUriPairs(cimCollection),
                        new StringBuilder());

        // mermaid config
        appendConfig(renderContext);
        renderContext.mermaidString.append("classDiagram\n");

        // actual mermaid String generation
        appendPackages(renderContext);

        appendClassInheritance(renderContext);

        appendAssociations(renderContext);

        appendOnClickFunctionality(renderContext);

        return new MermaidDTO(renderContext.mermaidString.toString());
    }

    private static final String ON_CLICK_CALLBACK_FUNCTION_NAME = "getClassInformation";

    private static final String TAB = "    ";

    private static final String DARK_GRAY = "#303030";

    @Getter
    public enum MermaidThemeConfig {
        THEME("base"),
        FONT_FAMILY("sans-serif"),
        TEXT_COLOR(DARK_GRAY),
        PRIMARY_COLOR("#e0e0e0"),
        PRIMARY_BORDER_COLOR(DARK_GRAY),
        LINE_COLOR(DARK_GRAY);

        private final String value;

        MermaidThemeConfig(String value) {
            this.value = value;
        }
    }

    private void appendConfig(RenderContext renderContext) {
        renderContext
                .mermaidString
                .append("---\n")
                .append("config:\n")
                .append(TAB)
                .append("theme: ")
                .append(MermaidThemeConfig.THEME.getValue())
                .append("\n")
                .append(TAB)
                .append("themeVariables:\n")
                .append(TAB)
                .append(TAB)
                .append("fontFamily: \"")
                .append(MermaidThemeConfig.FONT_FAMILY.getValue())
                .append("\"\n")
                .append(TAB)
                .append(TAB)
                .append("textColor: \"")
                .append(MermaidThemeConfig.TEXT_COLOR.getValue())
                .append("\"\n")
                .append(TAB)
                .append(TAB)
                .append("primaryColor: \"")
                .append(MermaidThemeConfig.PRIMARY_COLOR.getValue())
                .append("\"\n")
                .append(TAB)
                .append(TAB)
                .append("primaryBorderColor: \"")
                .append(MermaidThemeConfig.PRIMARY_BORDER_COLOR.getValue())
                .append("\"\n")
                .append(TAB)
                .append(TAB)
                .append("lineColor: \"")
                .append(MermaidThemeConfig.LINE_COLOR.getValue())
                .append("\"\n")
                .append("---\n");
    }

    /** Appends all packages to the mermaid String */
    private void appendPackages(RenderContext renderContext) {
        for (var cimPackage : renderContext.cimCollection.getPackages()) {
            var packageContents = getClassMermaidStrings(renderContext, cimPackage);
            packageContents.addAll(getEnumMermaidStrings(renderContext, cimPackage));
            renderContext.mermaidString.append(
                    new CIMPackageToMermaidBuilder(cimPackage, packageContents).build());
        }
        var varNotInPackageContents = getClassMermaidStrings(renderContext, null);
        varNotInPackageContents.addAll(getEnumMermaidStrings(renderContext, null));
        renderContext.mermaidString.append(
                new CIMPackageToMermaidBuilder(null, varNotInPackageContents).build());
    }

    /**
     * Generates the mermaid Strings for all classes in a package
     *
     * @param cimPackage The package to generate the classes for
     * @return A list of mermaid Strings for the classes in the package
     */
    private List<StringBuilder> getClassMermaidStrings(
            RenderContext renderContext, CIMPackage cimPackage) {
        var classMermaidStrings = new ArrayList<StringBuilder>();
        for (var cimClass : renderContext.cimCollection.getClasses()) {
            if (classIsNotInPackage(cimClass, cimPackage)) {
                continue;
            }
            classMermaidStrings.add(
                    new CIMClassToMermaidBuilder(
                                    cimClass,
                                    renderContext.uriToUUIDMap.get(cimClass.getUri().toString()))
                            .appendClassContents(
                                    getAttributeMermaidStrings(renderContext, cimClass))
                            .build());
        }
        return classMermaidStrings;
    }

    private List<StringBuilder> getEnumMermaidStrings(
            RenderContext renderContext, CIMPackage cimPackage) {
        var enumMermaidStrings = new ArrayList<StringBuilder>();
        for (var cimEnumClass : renderContext.cimCollection.getEnums()) {
            if (classIsNotInPackage(cimEnumClass, cimPackage)) {
                continue;
            }
            enumMermaidStrings.add(
                    new CIMClassToMermaidBuilder(
                                    cimEnumClass,
                                    renderContext.uriToUUIDMap.get(
                                            cimEnumClass.getUri().toString()))
                            .appendClassContents(
                                    getEnumEntryMermaidStrings(renderContext, cimEnumClass))
                            .build());
        }
        return enumMermaidStrings;
    }

    private List<StringBuilder> getEnumEntryMermaidStrings(
            RenderContext renderContext, CIMClass cimEnumClass) {
        var enumEntryMermaidStrings = new ArrayList<StringBuilder>();
        for (var cimEnumEntry : renderContext.cimCollection.getEnumEntries()) {
            if (!cimEnumEntry.getType().getUri().equals(cimEnumClass.getUri())) {
                continue;
            }
            enumEntryMermaidStrings.add(new CIMEnumEntryToMermaidBuilder(cimEnumEntry).build());
        }
        return enumEntryMermaidStrings;
    }

    /**
     * Generates the mermaid Strings for all attributes in a class.
     *
     * @param cimClass The class to generate the attributes for.
     * @return A list of mermaid Strings for the attributes in the class.
     */
    private List<StringBuilder> getAttributeMermaidStrings(
            RenderContext renderContext, CIMClass cimClass) {
        var attributeMermaidStrings = new ArrayList<StringBuilder>();
        for (var cimAttribute : renderContext.cimCollection.getAttributes()) {
            if (!cimAttribute.getDomain().getUri().equals(cimClass.getUri())) {
                continue;
            }
            attributeMermaidStrings.add(new CIMAttributeToMermaidBuilder(cimAttribute).build());
        }
        return attributeMermaidStrings;
    }

    private void appendClassInheritance(RenderContext renderContext) {
        for (var cimClass : renderContext.cimCollection.getClasses()) {
            if (cimClass.getSuperClass() == null) {
                continue;
            }
            var classUUID = renderContext.uriToUUIDMap.get(cimClass.getUri().toString());
            var superClassUUID =
                    renderContext.uriToUUIDMap.get(cimClass.getSuperClass().getUri().toString());
            renderContext
                    .mermaidString
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

    private void appendAssociations(RenderContext renderContext) {
        var handledAssociations = new ArrayList<URI>();
        for (var from : renderContext.cimCollection.getAssociations()) {
            var to =
                    renderContext.cimCollection.getAssociations().stream()
                            .filter(
                                    possibleTo ->
                                            from.getInverseRoleName()
                                                    .getUri()
                                                    .equals(possibleTo.getUri()))
                            .findFirst()
                            .orElse(null);
            if (to == null
                    || (handledAssociations.contains(from.getUri())
                            && handledAssociations.contains(to.getUri()))) {
                continue;
            }

            renderContext
                    .mermaidString
                    .append(TAB)
                    .append(
                            new CIMAssociationToMermaidBuilder(from, to, renderContext.uriToUUIDMap)
                                    .build());
            handledAssociations.add(from.getUri());
            handledAssociations.add(to.getUri());
        }
    }

    /**
     * Generates the line for the mermaid String that allows the user to click on the class and call
     * a function.
     */
    private void appendOnClickFunctionality(RenderContext renderContext) {
        var clickableEntitylist = new ArrayList<>(renderContext.cimCollection.getClasses());
        clickableEntitylist.addAll(renderContext.cimCollection.getEnums());
        clickableEntitylist.forEach(
                cimEntity -> {
                    var uuid = renderContext.uriToUUIDMap.get(cimEntity.getUri().toString());
                    renderContext
                            .mermaidString
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

    /** Helper record storing the rendering context shared across method calls */
    private record RenderContext(
            CIMCollection cimCollection,
            Map<String, UUID> uriToUUIDMap,
            StringBuilder mermaidString) {}

    private boolean classIsNotInPackage(CIMClass cimClass, CIMPackage cimPackage) {
        if (cimClass.getBelongsToCategory() == null) {
            return cimPackage != null;
        }
        if (cimPackage == null) {
            return true;
        }
        return !cimClass.getBelongsToCategory().getUri().equals(cimPackage.getUri());
    }
}
