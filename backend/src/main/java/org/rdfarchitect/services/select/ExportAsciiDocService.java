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
import org.rdfarchitect.api.dto.association.AssociationDTO;
import org.rdfarchitect.api.dto.association.AssociationPairDTO;
import org.rdfarchitect.api.dto.attributes.AttributeDTO;
import org.rdfarchitect.api.dto.enumentries.EnumEntryDTO;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Exports a graph as AsciiDoc. The document describes the same content as the HTML export - the
 * same sections, the same tables, the same package diagram references - but as an AsciiDoc source
 * that can be rendered on its own or included into a larger document.
 *
 * <p>Sections start at level 1 ({@code ==}), so the file is a valid standalone document. Include it
 * with {@code include::<profile>.adoc[leveloffset=+n]} to place it deeper in another document.
 *
 * <p>Class anchors are prefixed with the name of the exported graph, so that several profiles can
 * be included into the same document without their anchors colliding.
 */
@Service
@RequiredArgsConstructor
public class ExportAsciiDocService implements ExportGraphAsciiDocUseCase {

    private static final String DEFAULT_NAME = "default";

    /** Characters that would start AsciiDoc inline formatting and are therefore passed through. */
    private static final String INLINE_MARKS = "*_`#^~";

    /** Characters that would start a block if they were the first character of a line. */
    private static final String BLOCK_MARKS = ".*-=+/|:;[";

    private final GetClassListUseCase getClassListUseCase;

    @Override
    public byte[] exportGraphAsAsciiDoc(
            GraphIdentifier graphIdentifier, String fileEnding, boolean embedDiagrams) {
        var classList = getClassListUseCase.getFullClassList(graphIdentifier);
        var context =
                new Context(
                        anchorPrefix(graphIdentifier),
                        classList.stream()
                                .map(ClassUMLAdaptedDTO::getLabel)
                                .filter(label -> label != null && !label.isEmpty())
                                .collect(Collectors.toUnmodifiableSet()),
                        fileEnding,
                        embedDiagrams);

        var document = buildStereotypeList() + buildSections(classList, context);

        return document.getBytes(StandardCharsets.UTF_8);
    }

    /** Prefix of all anchors of this document, derived from the name of the exported graph. */
    private String anchorPrefix(GraphIdentifier graphIdentifier) {
        var graphUri = graphIdentifier.graphUri();
        var name = DEFAULT_NAME;
        if (graphUri != null && !graphUri.isBlank() && !DEFAULT_NAME.equals(graphUri)) {
            try {
                var suffix = new URI(graphUri).getSuffix();
                name = suffix.isBlank() ? graphUri : suffix;
            } catch (IllegalArgumentException _) {
                name = graphUri;
            }
        }
        return identifier(name);
    }

    private String buildStereotypeList() {
        var builder = new StringBuilder();
        builder.append("List of stereotypes to categorize subProfiles:\n\n");
        for (var stereotype : ProfileDocumentationStructure.STEREOTYPES) {
            builder.append("* ").append(stereotype).append("\n");
        }
        builder.append("\n");
        return builder.toString();
    }

    private String buildSections(List<ClassUMLAdaptedDTO> classList, Context context) {
        var builder = new StringBuilder();
        for (var section : ProfileDocumentationStructure.buildSections(classList)) {
            builder.append("== ").append(section.title()).append("\n\n");
            for (var classUMLAdaptedDTO : section.classes()) {
                builder.append(
                        buildClass(section.stereotype(), classUMLAdaptedDTO, classList, context));
            }
        }
        return builder.toString();
    }

    private String buildClass(
            String stereotype,
            ClassUMLAdaptedDTO classUMLAdaptedDTO,
            List<ClassUMLAdaptedDTO> fullClassList,
            Context context) {
        var builder = new StringBuilder();

        builder.append("[[").append(context.anchorOf(classUMLAdaptedDTO.getLabel())).append("]]\n");
        builder.append("=== ").append(escape(classUMLAdaptedDTO.getLabel()));
        if (stereotype != null && !stereotype.isEmpty()) {
            builder.append(" (").append(stereotype).append(")");
        }
        builder.append("\n\n");

        builder.append(buildPackageReference(classUMLAdaptedDTO.getBelongsToCategory(), context));

        if (classUMLAdaptedDTO.getComment() != null && !classUMLAdaptedDTO.getComment().isEmpty()) {
            builder.append(paragraph(classUMLAdaptedDTO.getComment()));
        }

        if (ProfileDocumentationStructure.isEnumeration(classUMLAdaptedDTO)) {
            builder.append(buildEnumEntries(classUMLAdaptedDTO));
        } else {
            builder.append(buildNativeMembers(stereotype, classUMLAdaptedDTO, context));
            builder.append(buildInheritedMembers(classUMLAdaptedDTO, fullClassList, context));
        }

        return builder.toString();
    }

    private String buildPackageReference(BelongsToCategoryDTO category, Context context) {
        if (category == null) {
            return "";
        }
        var image =
                "images/"
                        + (category.getUuid() != null
                                ? category.getUuid().toString()
                                : DEFAULT_NAME)
                        + "."
                        + context.fileEnding();
        var label = escape(category.getLabel());

        if (context.embedDiagrams()) {
            return "." + label + "\nimage::" + image + "[" + label + "]\n\n";
        }
        return "link:" + image + "[" + label + "]\n\n";
    }

    private String buildNativeMembers(
            String stereotype, ClassUMLAdaptedDTO classUMLAdaptedDTO, Context context) {
        var attributes = classUMLAdaptedDTO.getAttributes();
        var associationPairs = classUMLAdaptedDTO.getAssociationPairs();

        if ((attributes == null || attributes.isEmpty())
                && (associationPairs == null || associationPairs.isEmpty())) {
            return "";
        }

        var builder = new StringBuilder();
        builder.append("==== Native Members");
        if (stereotype != null && !stereotype.isEmpty()) {
            builder.append(" (").append(stereotype).append(")");
        }
        builder.append("\n\n");

        builder.append("[cols=\"4*\"]\n|===\n");
        if (attributes != null) {
            for (var attribute : attributes) {
                builder.append(buildAttributeRow(attribute, context));
            }
        }
        if (associationPairs != null) {
            for (var pair : associationPairs) {
                builder.append(buildAssociationRow(pair, context));
            }
        }
        builder.append("|===\n\n");

        return builder.toString();
    }

    private String buildAttributeRow(AttributeDTO attribute, Context context) {
        return row(
                cell(attribute.getLabel()),
                cell(attribute.getMultiplicity()),
                typeCell(
                        attribute.getDataType() != null ? attribute.getDataType().getLabel() : null,
                        context),
                cell(attribute.getComment()));
    }

    private String buildAssociationRow(AssociationPairDTO pair, Context context) {
        AssociationDTO from = pair.getFrom();
        if (from == null) {
            return "";
        }
        return row(
                cell(from.getLabel()),
                cell(from.getMultiplicity()),
                typeCell(from.getRange() != null ? from.getRange().getLabel() : null, context),
                cell(from.getComment()));
    }

    private String buildInheritedMembers(
            ClassUMLAdaptedDTO classUMLAdaptedDTO,
            List<ClassUMLAdaptedDTO> classList,
            Context context) {
        var ancestors =
                ProfileDocumentationStructure.resolveAncestorChain(classUMLAdaptedDTO, classList);
        if (ancestors.isEmpty()) {
            return "";
        }

        var builder = new StringBuilder();
        builder.append("==== Inherited Members\n\n");

        var pass = new StringBuilder();
        for (var ancestor : ancestors) {
            pass.append("->").append(ancestor.getLabel());
        }
        builder.append("_Inheritance pass: ").append(escape(pass.toString())).append("_\n\n");

        builder.append("[cols=\"5*\"]\n|===\n");
        for (var ancestor : ancestors) {
            if (ancestor.getAttributes() != null) {
                for (var attribute : ancestor.getAttributes()) {
                    builder.append(buildInheritedAttributeRow(attribute, ancestor, context));
                }
            }
            if (ancestor.getAssociationPairs() != null) {
                for (var pair : ancestor.getAssociationPairs()) {
                    builder.append(buildInheritedAssociationRow(pair, ancestor, context));
                }
            }
        }
        builder.append("|===\n\n");

        return builder.toString();
    }

    private String buildInheritedAttributeRow(
            AttributeDTO attribute, ClassUMLAdaptedDTO ancestor, Context context) {
        return row(
                cell(attribute.getLabel()),
                cell(attribute.getMultiplicity()),
                typeCell(
                        attribute.getDataType() != null ? attribute.getDataType().getLabel() : null,
                        context),
                "",
                "see " + context.reference(ancestor.getLabel()));
    }

    private String buildInheritedAssociationRow(
            AssociationPairDTO pair, ClassUMLAdaptedDTO ancestor, Context context) {
        AssociationDTO from = pair.getFrom();
        if (from == null) {
            return "";
        }
        return row(
                cell(from.getLabel()),
                cell(from.getMultiplicity()),
                typeCell(from.getRange() != null ? from.getRange().getLabel() : null, context),
                "",
                "see " + context.reference(ancestor.getLabel()));
    }

    private String buildEnumEntries(ClassUMLAdaptedDTO classUMLAdaptedDTO) {
        var entries = classUMLAdaptedDTO.getEnumEntries();
        if (entries == null || entries.isEmpty()) {
            return "";
        }

        var builder = new StringBuilder();
        builder.append("==== Enumeration Values\n\n");
        builder.append("[cols=\"2*\"]\n|===\n");
        for (var entry : entries) {
            builder.append(buildEnumEntryRow(entry));
        }
        builder.append("|===\n\n");
        return builder.toString();
    }

    private String buildEnumEntryRow(EnumEntryDTO entry) {
        return row(cell(entry.getLabel()), cell(entry.getComment()));
    }

    /** One table row, all cells on a single line. */
    private String row(String... cells) {
        var builder = new StringBuilder();
        for (var cell : cells) {
            builder.append("|").append(cell).append(" ");
        }
        return builder.toString().stripTrailing() + "\n";
    }

    private String cell(String value) {
        return protectLineStart(escape(value)).replace("|", "\\|");
    }

    /** Cell containing a class reference; only classes of this export can be linked. */
    private String typeCell(String label, Context context) {
        return label == null || label.isEmpty() ? "" : context.reference(label);
    }

    private String paragraph(String value) {
        return protectLineStart(escape(value)) + "\n\n";
    }

    /**
     * Escapes text for use inside an AsciiDoc line: everything is collapsed onto a single line and
     * characters that would start inline formatting are passed through verbatim.
     */
    private static String escape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        var collapsed = value.replaceAll("\\s+", " ").trim();
        var builder = new StringBuilder();
        for (var character : collapsed.toCharArray()) {
            if (INLINE_MARKS.indexOf(character) >= 0) {
                builder.append("++").append(character).append("++");
            } else {
                builder.append(character);
            }
        }
        return builder.toString();
    }

    /** Neutralises a leading character that would otherwise start a block or a list item. */
    private static String protectLineStart(String escaped) {
        if (!escaped.isEmpty() && BLOCK_MARKS.indexOf(escaped.charAt(0)) >= 0) {
            return "{empty}" + escaped;
        }
        return escaped;
    }

    /** Turns a label into a valid AsciiDoc identifier. */
    private static String identifier(String value) {
        var sanitized = value.replaceAll("[^A-Za-z0-9_]", "_");
        if (sanitized.isEmpty() || !Character.isLetter(sanitized.charAt(0))) {
            sanitized = "_" + sanitized;
        }
        return sanitized;
    }

    /**
     * @param anchorPrefix prefix of all anchors of this document
     * @param exportedLabels labels of all classes contained in this export
     * @param fileEnding file ending of the package diagram files
     * @param embedDiagrams whether the package diagram is shown in the document instead of only
     *     being linked
     */
    private record Context(
            String anchorPrefix,
            Set<String> exportedLabels,
            String fileEnding,
            boolean embedDiagrams) {

        String anchorOf(String label) {
            return anchorPrefix + "_" + identifier(label == null ? "" : label);
        }

        /** A cross reference if the class is part of this export, its plain name otherwise. */
        String reference(String label) {
            if (label == null || label.isEmpty()) {
                return "";
            }
            if (!exportedLabels.contains(label)) {
                return escape(label);
            }
            return "xref:" + anchorOf(label) + "[" + escape(label) + "]";
        }
    }
}
