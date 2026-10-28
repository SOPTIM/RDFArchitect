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

package org.rdfarchitect.services.update.graph;

import lombok.experimental.UtilityClass;

import org.rdfarchitect.services.update.graph.ImportGraphsUseCase.ImportWarning;
import org.rdfarchitect.services.update.graph.ImportGraphsUseCase.UncarriedItem;
import org.rdfarchitect.xmi.map.XmiProfileMapper.DroppedMember;
import org.rdfarchitect.xmi.map.XmiProfileMapper.UnresolvedType;
import org.rdfarchitect.xmi.map.XmiProfileMapper.WrittenProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * What one written profile did not carry over, as the dialog shows it.
 *
 * <p>Every way a schema can come out holding less than the export does is reported here, and the
 * rule is the same for all of them: name what was lost and say why, never pass over it quietly. An
 * import that silently writes less than it was given is worse than one that refuses.
 */
@UtilityClass
class XmiImportWarnings {

    private final Logger logger = LoggerFactory.getLogger(XmiImportWarnings.class);

    /** Adds one warning per kind of loss, and none for a profile that lost nothing. */
    void reportOn(
            String profileName,
            String graphUri,
            WrittenProfile written,
            List<ImportWarning> warnings) {
        add(warnings, profileName, graphUri, unresolvedTypes(profileName, written));
        add(warnings, profileName, graphUri, duplicateClasses(profileName, written));
        add(warnings, profileName, graphUri, droppedMembers(profileName, written));
        add(warnings, profileName, graphUri, unmappedOntologyFields(written));
    }

    private void add(
            List<ImportWarning> warnings,
            String profileName,
            String graphUri,
            List<UncarriedItem> described) {
        if (!described.isEmpty()) {
            warnings.add(new ImportWarning(profileName, graphUri, described));
        }
    }

    /**
     * The attributes that were written without a datatype, naming what the export points at where
     * it says so at all.
     *
     * <p>This is what a partial export costs. Enterprise Architect writes an {@code EAStub} for
     * every element it leaves out, which is enough to name the missing type and not enough to
     * declare it, so the attribute points at a resource nothing defines and the caller is told
     * which package to export alongside next time.
     */
    private List<UncarriedItem> unresolvedTypes(String profileName, WrittenProfile written) {
        if (written.unresolvedTypes().isEmpty()) {
            return List.of();
        }
        var missingTypes =
                written.unresolvedTypes().stream()
                        .map(UnresolvedType::missingType)
                        .filter(Objects::nonNull)
                        .distinct()
                        .sorted()
                        .toList();
        logger.warn(
                "Profile '{}': {} attribute(s) typed with something the export does not declare."
                        + " The export refers to {} but does not contain {}.",
                profileName,
                written.unresolvedTypes().size(),
                missingTypes.isEmpty() ? "types" : missingTypes,
                missingTypes.isEmpty() ? "them" : "it");
        return written.unresolvedTypes().stream().map(XmiImportWarnings::untyped).toList();
    }

    /**
     * The classes that were left out for sharing a uri with one already written.
     *
     * <p>Not a failure of the import: the resource is there, under the first of the two
     * declarations. What is lost is whatever the second said differently, which is why it is named
     * rather than passed over.
     */
    private List<UncarriedItem> duplicateClasses(String profileName, WrittenProfile written) {
        if (written.duplicateClasses().isEmpty()) {
            return List.of();
        }
        logger.warn(
                "Profile '{}': {} class(es) the export declares more than once; the first"
                        + " declaration of each is kept.",
                profileName,
                written.duplicateClasses().size());
        return written.duplicateClasses().stream()
                .map(
                        duplicate ->
                                new UncarriedItem(
                                        duplicate.name(),
                                        "Declared more than once; only the first declaration is"
                                                + " imported"))
                .toList();
    }

    /**
     * The members of written classes that could not be written themselves, which is an extension
     * attribute on a class that is none while the export states no extension namespace. The class
     * is in the schema and the attribute is not, so the loss is invisible without this.
     */
    private List<UncarriedItem> droppedMembers(String profileName, WrittenProfile written) {
        if (written.droppedMembers().isEmpty()) {
            return List.of();
        }
        logger.warn(
                "Profile '{}': {} attribute(s) stereotyped as an extension could not be named,"
                        + " the export stating no extension namespace. They are not imported.",
                profileName,
                written.droppedMembers().size());
        return written.droppedMembers().stream()
                .map(dropped -> new UncarriedItem(dropped.memberName(), describe(dropped)))
                .toList();
    }

    private String describe(DroppedMember dropped) {
        var stereotypes =
                dropped.stereotypes().isEmpty()
                        ? "an extension"
                        : String.join(", ", dropped.stereotypes());
        return "Stereotyped "
                + stereotypes
                + ", and the export states no extension namespace to name it in. Supply one with"
                + " the import to carry it over.";
    }

    /**
     * The ontology header fields the export states under a name RDFArchitect has no predicate for,
     * so they are not written.
     */
    private List<UncarriedItem> unmappedOntologyFields(WrittenProfile written) {
        return written.unmappedOntologyFields().stream()
                .map(
                        field ->
                                new UncarriedItem(
                                        field.name(),
                                        "No ontology field of that name; the header field is not"
                                                + " imported"))
                .toList();
    }

    /** An untyped attribute as the dialog shows it: the attribute and what it wanted to be. */
    private UncarriedItem untyped(UnresolvedType unresolved) {
        var name = unresolved.attributeUri();
        var local = name.substring(name.indexOf('#') + 1);
        return new UncarriedItem(
                local,
                unresolved.missingType() == null
                        ? "The export states no type for this attribute. The attribute is kept"
                                + " without one."
                        : unresolved.missingType()
                                + " is used as a type here, but the export holds no definition of"
                                + " it. The attribute is kept, with a dangling reference.");
    }
}
