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

package org.rdfarchitect.services.select;

import lombok.RequiredArgsConstructor;

import org.rdfarchitect.api.dto.BelongsToCategoryDTO;
import org.rdfarchitect.api.dto.ClassUMLAdaptedDTO;
import org.rdfarchitect.api.dto.SuperClassDTO;
import org.rdfarchitect.api.dto.association.AssociationDTO;
import org.rdfarchitect.api.dto.association.AssociationPairDTO;
import org.rdfarchitect.api.dto.attributes.AttributeDTO;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.springframework.stereotype.Service;

import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExportHTMLService implements ExportGraphHTMLUseCase {

    private final GetClassListUseCase getClassListUseCase;
    private final JsonMapper.Builder builder;

    @Override
    public byte[] exportGraphAsHTML(GraphIdentifier graphIdentifier) {

        var classList = getClassListUseCase.getFullClassList(graphIdentifier);

        String html = header() + buildBody(classList);

        return html.getBytes(StandardCharsets.UTF_8);
    }

    private String header() {
        return "<!DOCTYPE html>\n"
                + "<head>\n"
                + "<title>Profile Documentation</title>\n"
                + "<META content=\"text/html; charset=utf-8\" http-equiv=Content-Type>\n"
                + style()
                + "\n</head>\n";
    }

    private String style() {
        return """
            <style>
            :root {
                /* soptim colors */
                --color-soptim-blau: #009ee0;
                --color-soptim-dunkelgrau: #7d7d7d;
                --color-soptim-dunkelblau: #14162b;
                --color-soptim-pink: #e74890;
                --color-soptim-orange: #f0881a;

                /* opencgmes colors */
                --color-blue: #1f75cb;
                --color-lightblue: #e3eef8;
                --color-orange: #fc6d26;
                --color-purple: #6e49cb;
                --color-red: #db3b21;
                --color-gray: #2e2e2e;
                --color-lightgray: #e0e0e0;
                --color-white: #ffffff;
                --color-text-subtle: #787878;
                --color-background-subtle: #f1f1f1;

                --color-default-text: #303030;
                --color-default-background: #e0e0e0;

                --color-window-background: #f9f9f9;

                --color-border: #e0e0e0;
                --color-border-select: #1f75cb;
                --color-background-select: rgba(31, 117, 203, 0.08);
                --color-border-strong: #c6c6c6;

                --color-nav-surface: #ffffff;
                --color-nav-border: #dfe6ef;
                --color-nav-hover-background: #e8efff;
                --color-nav-active-background: #d8e7ff;
                --color-nav-active-text: #0f3e78;

                --color-nav-open-class-background: #e8f1fc;
                --color-nav-text: #1f2937;
                --color-nav-secondary-text: #5c6676;
                --color-nav-badge-background: #1f75cb;
                --color-nav-badge-text: #ffffff;
                --color-nav-external-badge-background: #f0881a;

                --color-class-node-upper-background: var(--color-default-background);
                --color-class-node-lower-background: #f2f2f2;
                --color-inheritance-edge: var(--color-soptim-dunkelgrau);
            }

            * {
                box-sizing: border-box;
            }

            body {
                font-family:
                    "Bahnschrift",
                    -apple-system,
                    BlinkMacSystemFont,
                    "Segoe UI",
                    Roboto,
                    Oxygen,
                    Ubuntu,
                    Cantarell,
                    "Open Sans",
                    "Helvetica Neue",
                    sans-serif;
                background-color: var(--color-window-background);
                color: var(--color-default-text);
                margin: 0;
                padding: 2rem;
                line-height: 1.5;
            }

            h1 {
                font-size: 1.6rem;
                color: var(--color-soptim-dunkelblau);
                border-bottom: 2px solid var(--color-blue);
                padding-bottom: 0.4rem;
                margin-top: 2.5rem;
            }

            h1:first-of-type {
                margin-top: 1.5rem;
            }

            h3 {
                font-size: 1.05rem;
                color: var(--color-blue);
                margin-bottom: 0.5rem;
                margin-top: 1.2rem;
            }

            h4 {
                font-size: 0.85rem;
                color: var(--color-nav-secondary-text);
                font-weight: normal;
                font-style: italic;
                margin: 0.2rem 0 0.8rem 0;
            }

            .intro {
                background: var(--color-nav-surface);
                border: 1px solid var(--color-nav-border);
                border-radius: 8px;
                padding: 1rem 1.5rem;
                box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08), 0 1px 2px rgba(0, 0, 0, 0.06);
                max-width: 500px;
            }

            .intro ul {
                margin: 0.5rem 0 0 0;
                padding-left: 1.2rem;
            }

            .group {
                background: var(--color-nav-surface);
                border: 1px solid var(--color-nav-border);
                border-radius: 8px;
                box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08), 0 1px 2px rgba(0, 0, 0, 0.06);
                padding: 1.25rem 1.5rem;
                margin-bottom: 1.5rem;
            }

            .group a {
                text-decoration: none;
            }

            h2.concrete,
            h2.abstract {
                margin: 0 0 0.5rem 0;
                font-size: 1.25rem;
            }

            h2.concrete {
                color: var(--color-soptim-dunkelblau);
            }

            h2.abstract {
                color: var(--color-purple);
                font-style: italic;
            }

            h2.concrete::before {
                content: "● ";
                color: var(--color-blue);
                font-size: 0.7rem;
                vertical-align: middle;
            }

            h2.abstract::before {
                content: "◇ ";
                color: var(--color-purple);
                font-size: 0.8rem;
                vertical-align: middle;
            }

            p.package {
                font-style: italic;
                color: var(--color-text-subtle);
                font-size: 0.9rem;
                margin: 0 0 0.6rem 0;
            }

            p.package a {
                color: var(--color-blue);
            }

            p.comment {
                color: var(--color-default-text);
                background: var(--color-background-subtle);
                border-left: 3px solid var(--color-blue);
                padding: 0.5rem 0.8rem;
                margin: 0.5rem 0 1rem 0;
                border-radius: 0 4px 4px 0;
            }

            table {
                border-collapse: collapse;
                width: 100%;
                margin-bottom: 0.75rem;
                font-size: 0.9rem;
            }

            table, th, td {
                border: 1px solid var(--color-border);
            }

            th, td {
                padding: 0.5rem 0.7rem;
                text-align: left;
                vertical-align: top;
            }

            tr:nth-child(even) {
                background-color: var(--color-background-subtle);
            }

            tr:hover {
                background-color: var(--color-nav-hover-background);
            }

            p.attribut,
            p.inheritattribut {
                font-weight: 600;
                color: var(--color-soptim-dunkelblau);
                margin: 0;
            }

            p.role,
            p.inheritrole {
                font-weight: 600;
                color: var(--color-blue);
                margin: 0;
            }

            p.cardinality {
                text-align: center;
                color: var(--color-text-subtle);
                margin: 0;
                font-variant-numeric: tabular-nums;
            }

            p.type {
                margin: 0;
            }

            p.type a {
                color: var(--color-orange);
                text-decoration: none;
                font-weight: 500;
            }

            p.type a:hover {
                text-decoration: underline;
            }

            a.superclass {
                font-style: italic;
                color: var(--color-blue);
                text-decoration: none;
            }

            a.superclass:hover {
                text-decoration: underline;
            }
            </style>""";
    }

    private String buildBody(List<ClassUMLAdaptedDTO> classList) {
        return "<body>\n"
                + "<div class=\"intro\">\n"
                + "<p>List of stereotypes to categorize subProfiles:</p>\n"
                + "<ul>\n"
                + "<li>ShortCircuit</li>\n"
                + "<li>Description</li>\n"
                + "<li>Operation</li>\n"
                + "<li>European</li>\n"
                + "<li>Abstract</li>\n"
                + "<li>Entsoe</li>\n"
                + "</ul>\n"
                + "</div>\n"
                + buildStereotypeSections(classList)
                + "</body>";
    }

    private String buildStereotypeSections(List<ClassUMLAdaptedDTO> classList) {
        var builder = new StringBuilder();
        var stereotypes =
                List.of(
                        CIMStereotypes.shortCircuitString,
                        CIMStereotypes.descriptionString,
                        CIMStereotypes.operationString,
                        CIMStereotypes.europeanString,
                        CIMStereotypes.entsoeString);

        var processedUuids = new java.util.HashSet<java.util.UUID>();

        for (var stereotype : stereotypes) {
            var stereotypeClasses =
                    classList.stream()
                            .filter(
                                    c ->
                                            c.getStereotypes() != null
                                                    && c.getStereotypes().contains(stereotype))
                            .toList();
            if (!stereotypeClasses.isEmpty()) {
                builder.append(buildStereotypeSection(stereotype, stereotypeClasses, classList));
                stereotypeClasses.forEach(c -> processedUuids.add(c.getUuid()));
            }
        }

        var abstractClasses = classList.stream().filter(c -> !isConcrete(c)).toList();
        if (!abstractClasses.isEmpty()) {
            builder.append(buildAbstractSection(abstractClasses, classList));
            abstractClasses.forEach(c -> processedUuids.add(c.getUuid()));
        }

        var remainingClasses =
                classList.stream().filter(c -> !processedUuids.contains(c.getUuid())).toList();
        if (!remainingClasses.isEmpty()) {
            builder.append(buildRemainingSection(remainingClasses, classList));
        }

        return builder.toString();
    }

    private String buildAbstractSection(
            List<ClassUMLAdaptedDTO> sectionClasses, List<ClassUMLAdaptedDTO> fullClassList) {
        var builder = new StringBuilder();
        builder.append("<h1>Abstract Classes</h1>\n");
        for (var classUMLAdaptedDTO : sectionClasses) {
            builder.append(buildClass(null, classUMLAdaptedDTO, fullClassList));
        }
        return builder.toString();
    }

    private String buildRemainingSection(
            List<ClassUMLAdaptedDTO> sectionClasses, List<ClassUMLAdaptedDTO> fullClassList) {
        var builder = new StringBuilder();
        builder.append("<h1>Classes</h1>\n");
        for (var classUMLAdaptedDTO : sectionClasses) {
            builder.append(buildClass(null, classUMLAdaptedDTO, fullClassList));
        }
        return builder.toString();
    }

    private String buildStereotypeSection(
            String stereotype,
            List<ClassUMLAdaptedDTO> sectionClasses,
            List<ClassUMLAdaptedDTO> fullClassList) {
        var builder = new StringBuilder();
        builder.append("<h1>Classes (").append(stereotype).append(")</h1>\n");
        for (var classUMLAdaptedDTO : sectionClasses) {
            builder.append(buildClass(stereotype, classUMLAdaptedDTO, fullClassList));
        }
        return builder.toString();
    }

    private String buildClass(
            String stereotype,
            ClassUMLAdaptedDTO classUMLAdaptedDTO,
            List<ClassUMLAdaptedDTO> fullClassList) {
        var builder = new StringBuilder();
        var label = classUMLAdaptedDTO.getLabel();

        builder.append("<div id=\"").append(label).append("\" class=\"group\">\n");
        builder.append("<a href=\"#").append(label).append("\">\n");
        builder.append("<h2 class=\"")
                .append(isConcrete(classUMLAdaptedDTO) ? "concrete" : "abstract")
                .append("\">")
                .append(label);
        if (stereotype != null && !stereotype.isEmpty()) {
            builder.append(" (").append(stereotype).append(") ");
        }
        builder.append("</h2>\n");
        builder.append("</a>\n");

        builder.append(buildPackageReference(classUMLAdaptedDTO.getBelongsToCategory()));

        if (classUMLAdaptedDTO.getComment() != null && !classUMLAdaptedDTO.getComment().isEmpty()) {
            builder.append("<p class=\"comment\">")
                    .append(classUMLAdaptedDTO.getComment())
                    .append("</p>\n");
        }

        builder.append(buildNativeMembers(stereotype, classUMLAdaptedDTO));
        builder.append(buildInheritedMembers(classUMLAdaptedDTO, fullClassList));

        builder.append("</div>\n");

        return builder.toString();
    }

    private boolean isConcrete(ClassUMLAdaptedDTO classUMLAdaptedDTO) {
        return classUMLAdaptedDTO.getStereotypes() != null
                && classUMLAdaptedDTO.getStereotypes().contains(CIMStereotypes.concreteString);
    }

    private String buildPackageReference(BelongsToCategoryDTO category) {
        if (category == null) {
            return "";
        }
        String builder =
                "<p class=\"package\"><a href=\"images\\"
                        + category.getLabel()
                        + ".png\" target=\"_blanc\">"
                        + category.getLabel()
                        + " </a></p>\n";
        return builder;
    }

    private String buildNativeMembers(String stereotype, ClassUMLAdaptedDTO classUMLAdaptedDTO) {
        var attributes = classUMLAdaptedDTO.getAttributes();
        var associationPairs = classUMLAdaptedDTO.getAssociationPairs();

        if ((attributes == null || attributes.isEmpty())
                && (associationPairs == null || associationPairs.isEmpty())) {
            return "";
        }

        var builder = new StringBuilder();
        builder.append("<h3>Native Members");
        if (stereotype != null && !stereotype.isEmpty()) {
            builder.append(" (").append(stereotype).append(")");
        }
        builder.append("</h3>\n<table>\n");
        if (attributes != null) {
            for (var attribute : attributes) {
                builder.append(buildAttributeRow(attribute));
            }
        }

        if (associationPairs != null) {
            for (var pair : associationPairs) {
                builder.append(buildAssociationRow(pair));
            }
        }

        builder.append("</table>\n");

        return builder.toString();
    }

    private String buildAttributeRow(AttributeDTO attribute) {
        var builder = new StringBuilder();
        builder.append("<tr>\n");
        builder.append("<th>\n");
        builder.append("<p class=\"attribut\" id=\"")
                .append(attribute.getDomain())
                .append(".")
                .append(attribute.getLabel())
                .append("\">")
                .append(attribute.getLabel())
                .append(" </p>\n");
        builder.append("</th>\n");
        builder.append("<td>\n<p class=\"cardinality\">")
                .append(nullToEmpty(attribute.getMultiplicity()))
                .append("</p>\n</td>\n");
        builder.append("<td class=\"type\">\n<p class=\"type\">\n");
        if (attribute.getDataType() != null) {
            builder.append("<a href=\"#")
                    .append(attribute.getDataType().getLabel())
                    .append("\">")
                    .append(attribute.getDataType().getLabel())
                    .append("</a>\n");
        }
        builder.append("</p>\n</td>\n");
        builder.append("<td>\n<p class=\"comment\">")
                .append(nullToEmpty(attribute.getComment()))
                .append("</p>\n</td>\n");
        builder.append("</tr>\n");
        return builder.toString();
    }

    private String buildAssociationRow(AssociationPairDTO pair) {
        AssociationDTO to = pair.getTo();
        if (to == null) {
            return "";
        }

        var builder = new StringBuilder();
        builder.append("<tr>\n");
        builder.append("<td class=\"type\">\n<p class=\"type\">\n");
        builder.append("<p class=\"role\" id=\"")
                .append(to.getDomain())
                .append(".")
                .append(to.getLabel())
                .append("\">")
                .append(to.getLabel())
                .append(" </p>\n");
        builder.append("</p>\n</td>\n");
        builder.append("<td>\n<p class=\"cardinality\">[")
                .append(nullToEmpty(to.getMultiplicity()))
                .append("]</p>\n</td>\n");
        builder.append("<td>\n<p>");
        if (to.getRange() != null) {
            builder.append("<a href=\"#")
                    .append(to.getRange().getLabel())
                    .append("\">")
                    .append(to.getRange().getLabel())
                    .append("</a>");
        }
        builder.append("</p>\n</td>\n");
        builder.append("<td>\n<p class=\"comment\">")
                .append(nullToEmpty(to.getComment()))
                .append("</p>\n</td>\n");
        builder.append("</tr>\n");
        return builder.toString();
    }

    private String buildInheritedMembers(
            ClassUMLAdaptedDTO classUMLAdaptedDTO, List<ClassUMLAdaptedDTO> classList) {
        var ancestors = resolveAncestorChain(classUMLAdaptedDTO, classList);
        if (ancestors.isEmpty()) {
            return "";
        }

        var builder = new StringBuilder();
        builder.append("<h3>Inherited Members</h3>\n");

        var pass = new StringBuilder();
        for (var ancestor : ancestors) {
            pass.append("->").append(ancestor.getLabel());
        }
        builder.append("<h4> Inheritance pass: ").append(pass).append("</h4>\n");

        builder.append("<table>\n");
        for (var ancestor : ancestors) {
            if (ancestor != null && ancestor.getAttributes() != null) {
                for (var attribute : ancestor.getAttributes()) {
                    builder.append(buildInheritedAttributeRow(attribute, ancestor));
                }
            }
            if (ancestor != null && ancestor.getAssociationPairs() != null) {
                for (var pair : ancestor.getAssociationPairs()) {
                    builder.append(buildInheritedAssociationRow(pair, ancestor));
                }
            }
        }
        builder.append("</table>\n");

        return builder.toString();
    }

    private List<ClassUMLAdaptedDTO> resolveAncestorChain(
            ClassUMLAdaptedDTO classUMLAdaptedDTO, List<ClassUMLAdaptedDTO> classList) {
        var ancestors = new java.util.ArrayList<ClassUMLAdaptedDTO>();
        var visited = new java.util.HashSet<String>();

        var currentSuperClass = classUMLAdaptedDTO.getSuperClass();
        while (currentSuperClass != null) {
            var key = currentSuperClass.getPrefix() + "#" + currentSuperClass.getLabel();
            if (!visited.add(key)) {
                break;
            }

            var resolved = findClassByLabel(currentSuperClass, classList);
            if (resolved == null) {
                break;
            }

            ancestors.add(resolved);
            currentSuperClass = resolved.getSuperClass();
        }

        return ancestors;
    }

    private ClassUMLAdaptedDTO findClassByLabel(
            SuperClassDTO superClass, List<ClassUMLAdaptedDTO> classList) {
        return classList.stream()
                .filter(c -> c.getLabel() != null && c.getLabel().equals(superClass.getLabel()))
                .filter(
                        c ->
                                superClass.getPrefix() == null
                                        || superClass.getPrefix().equals(c.getPrefix()))
                .findFirst()
                .orElse(null);
    }

    private String buildInheritedAttributeRow(AttributeDTO attribute, ClassUMLAdaptedDTO ancestor) {
        var builder = new StringBuilder();
        builder.append("<tr>\n");
        builder.append("<th>\n");
        builder.append("<p class=\"inheritattribut\">")
                .append(attribute.getLabel())
                .append(" </p>\n");
        builder.append("</th>\n");
        builder.append("<td>\n<p class=\"cardinality\">")
                .append(nullToEmpty(attribute.getMultiplicity()))
                .append("</p>\n</td>\n");
        builder.append("<td class=\"type\">\n<p class=\"type\">\n");
        if (attribute.getDataType() != null) {
            builder.append("<a href=\"#")
                    .append(attribute.getDataType().getLabel())
                    .append("\">")
                    .append(attribute.getDataType().getLabel())
                    .append("</a>\n");
        }
        builder.append("</p>\n</td>\n");
        builder.append("<td>\n</td>\n");
        builder.append("<td>\n<p>see <a class=\"superclass\" href=\"#")
                .append(ancestor.getLabel())
                .append("\">")
                .append(ancestor.getLabel())
                .append("</a>\n</p>\n</td>\n");
        builder.append("</tr>\n");
        return builder.toString();
    }

    private String buildInheritedAssociationRow(
            AssociationPairDTO pair, ClassUMLAdaptedDTO ancestor) {
        AssociationDTO to = pair.getTo();
        if (to == null) {
            return "";
        }

        var builder = new StringBuilder();
        builder.append("<tr>\n");
        builder.append("<th>\n");
        builder.append("<p class=\"inheritrole\" id=\"")
                .append(ancestor.getLabel())
                .append(".")
                .append(to.getLabel())
                .append("\">")
                .append(to.getLabel())
                .append(" </p>\n");
        builder.append("</th>\n");
        builder.append("<td>\n<p class=\"cardinality\">")
                .append(nullToEmpty(to.getMultiplicity()))
                .append("</p>\n</td>\n");
        builder.append("<td class=\"type\">\n<p class=\"type\">\n");
        if (to.getRange() != null) {
            builder.append("<a href=\"#")
                    .append(to.getRange().getLabel())
                    .append("\">")
                    .append(to.getRange().getLabel())
                    .append("</a>\n");
        }
        builder.append("</p>\n</td>\n");
        builder.append("<td>\n</td>\n");
        builder.append("<td>\n<p>see <a class=\"superclass\" href=\"#")
                .append(ancestor.getLabel())
                .append("\">")
                .append(ancestor.getLabel())
                .append("</a>\n</p>\n</td>\n");
        builder.append("</tr>\n");
        return builder.toString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
