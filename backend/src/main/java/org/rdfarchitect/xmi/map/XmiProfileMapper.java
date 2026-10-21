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

package org.rdfarchitect.xmi.map;

import org.apache.jena.vocabulary.XSD;
import org.rdfarchitect.models.cim.data.dto.CIMAssociation;
import org.rdfarchitect.models.cim.data.dto.CIMAssociationPair;
import org.rdfarchitect.models.cim.data.dto.CIMAttribute;
import org.rdfarchitect.models.cim.data.dto.CIMClass;
import org.rdfarchitect.models.cim.data.dto.CIMEnumEntry;
import org.rdfarchitect.models.cim.data.dto.CIMPackage;
import org.rdfarchitect.models.cim.data.dto.facade.ICIMModelWriter;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSAssociationUsed;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSBelongsToCategory;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSInverseRoleName;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSIsDefault;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSIsFixed;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSStereotype;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSComment;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSDomain;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSSubClassOf;
import org.rdfarchitect.models.cim.data.dto.relations.RDFType;
import org.rdfarchitect.models.cim.data.dto.relations.datatype.RDFSRange;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;
import org.rdfarchitect.xmi.read.XmiAssociationEnd;
import org.rdfarchitect.xmi.read.XmiAttribute;
import org.rdfarchitect.xmi.read.XmiClass;
import org.rdfarchitect.xmi.read.XmiIndex;
import org.rdfarchitect.xmi.read.XmiProfile;
import org.rdfarchitect.xmi.read.XmiProfileSlice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Turns one profile of an export into a graph.
 *
 * <p>What a profile consists of is {@link XmiProfileSlice}'s to say: the package, its sub-packages,
 * and everything their attribute types and superclasses pull in. This maps that slice onto the
 * resources RDFArchitect stores and hands each to an {@link ICIMModelWriter}, which is the one
 * place that decides what a triple looks like.
 *
 * <p>Nothing is written until the namespaces are known, because a resource whose namespace is wrong
 * is worse than one that was never written: it loads, it looks right, and it matches nothing.
 *
 * <p>Two things an export states are deliberately not carried over. An {@code ea_guid} is a stable
 * identity across re-imports and the uuids here are minted fresh, because nothing re-imports yet
 * and identical uuids in unrelated workspaces are a consequence nobody has asked for. And an
 * association half that names a class outside the slice is left out entirely rather than written
 * pointing at nothing.
 */
public final class XmiProfileMapper {

    private final XmiIndex index;
    private final XmiProfile profile;
    private final XmiNamespaces namespaces;
    private final XmiUris uris;
    private final XmiDataTypes dataTypes;

    private XmiProfileMapper(XmiIndex index, XmiProfile profile, String suppliedExtension) {
        this.index = index;
        this.profile = profile;
        this.namespaces = XmiNamespaces.of(index, profile, suppliedExtension);
        this.uris = new XmiUris(namespaces);
        this.dataTypes = new XmiDataTypes(index, uris);
    }

    /**
     * Writes one profile of an export through {@code writer}.
     *
     * @param index the export, already read
     * @param profile which of its profiles to write
     * @param writer where the resources go
     * @throws XmiImportRefusal when the export does not hold enough of the profile to build it
     */
    public static void write(XmiIndex index, XmiProfile profile, ICIMModelWriter writer) {
        write(index, profile, writer, null);
    }

    /**
     * Writes one profile, with the extension namespace supplied from outside.
     *
     * @param suppliedExtension what the elements stereotyped as extensions are named with, where
     *     the export itself states nothing — a CGMES 2.4.15 profiles-only export does not
     */
    public static void write(
            XmiIndex index, XmiProfile profile, ICIMModelWriter writer, String suppliedExtension) {
        new XmiProfileMapper(index, profile, suppliedExtension).writeTo(writer);
    }

    private void writeTo(ICIMModelWriter writer) {
        index.getPrefixes().forEach(writer::bindPrefix);

        var slice = XmiProfileSlice.classIdsOf(index, profile.id());
        refuseIfUnnameable(slice);
        var categories = writePackages(writer, slice);
        var written = writeClasses(writer, slice, categories);
        writeAssociations(writer, written);
    }

    /**
     * Writes a package resource for every package the profile's classes are declared in, not only
     * for the profile's own.
     *
     * <p>A release does the same and it is not cosmetic: a CGMES 2.4.15 profile declares one {@code
     * cims:ClassCategory} per sub-package, each class points at the one it actually sits in, and a
     * single category for the whole profile would leave every class claiming a package the release
     * does not put it in.
     *
     * <p>The nesting is written too, which is more than the editing path can keep — see {@link
     * ICIMModelWriter}.
     */
    private Map<String, CIMSBelongsToCategory> writePackages(
            ICIMModelWriter writer, Set<String> slice) {
        var categories = new LinkedHashMap<String, CIMSBelongsToCategory>();
        for (var packageId : packagesOf(slice)) {
            var cimPackage = index.getPackages().get(packageId);
            categories.put(
                    packageId,
                    new CIMSBelongsToCategory(
                            new URI(uris.of(cimPackage)),
                            new RDFSLabel(cimPackage.getName(), "en"),
                            UUID.randomUUID()));
        }
        categories.forEach(
                (packageId, category) -> {
                    if (!index.isWithin(packageId, profile.id())) {
                        // A package the closure reached into rather than one the profile owns. A
                        // release references it from the classes it pulled in and declares it no
                        // more than this does — it belongs to the profile it came from.
                        return;
                    }
                    var cimPackage = index.getPackages().get(packageId);
                    writer.writePackage(
                            CIMPackage.builder()
                                    .uuid(category.getUuid())
                                    .uri(category.getUri())
                                    .label(category.getLabel())
                                    .comment(commentOf(cimPackage.getDocumentation()))
                                    .belongsToCategory(categories.get(cimPackage.getParentId()))
                                    .build());
                });
        return categories;
    }

    /** The profile's own package, and every package a class of the slice is declared in. */
    private Set<String> packagesOf(Set<String> slice) {
        var packageIds = new LinkedHashSet<String>();
        packageIds.add(profile.id());
        for (var classId : slice) {
            var cimClass = index.getClasses().get(classId);
            if (cimClass != null
                    && cimClass.getPackageId() != null
                    && index.getPackages().containsKey(cimClass.getPackageId())) {
                packageIds.add(cimClass.getPackageId());
            }
        }
        return packageIds;
    }

    /**
     * Stops before the first triple when the profile holds classes the export cannot name.
     *
     * <p>That happens for exactly one reason: a class stereotyped {@code Entsoe} or {@code
     * European} while the export states no extension namespace, which a CGMES 2.4.15 profiles-only
     * export does not. Those classes are not incidental — every profile of that release keeps its
     * {@code <Profile>Version} class among them, and the release names it and its ten attributes in
     * the extension namespace. Writing the profile without them yields a graph quietly missing its
     * own metadata, so the import says what it needs instead.
     */
    private void refuseIfUnnameable(Set<String> slice) {
        var unnameable =
                slice.stream()
                        .map(index.getClasses()::get)
                        .filter(Objects::nonNull)
                        .filter(cimClass -> !uris.canName(cimClass))
                        .map(XmiClass::getName)
                        .toList();
        if (unnameable.isEmpty()) {
            return;
        }
        throw new XmiImportRefusal(
                profile.name(),
                "The export states no extension namespace, and %d of the profile's classes need one: %s%s. A CGMES 2.4.15 profiles-only export leaves it out although its release uses it, so it has to be supplied with the import."
                        .formatted(
                                unnameable.size(),
                                String.join(", ", unnameable.stream().limit(5).toList()),
                                unnameable.size() > 5 ? ", …" : ""));
    }

    /**
     * Writes every class of the slice together with its members, and reports which of them made it.
     * A class the export names in a way no URI can hold has already been dropped by the reader; one
     * whose namespace the export does not state is dropped here, because naming it wrongly is worse
     * than leaving it out.
     */
    private Set<String> writeClasses(
            ICIMModelWriter writer,
            Set<String> slice,
            Map<String, CIMSBelongsToCategory> categories) {
        var written = new LinkedHashSet<String>();
        for (var classId : slice) {
            var cimClass = index.getClasses().get(classId);
            if (cimClass == null || !uris.canName(cimClass)) {
                continue;
            }
            writer.writeClass(
                    CIMClass.builder()
                            .uuid(UUID.randomUUID())
                            .uri(new URI(uris.of(cimClass)))
                            .label(new RDFSLabel(cimClass.getName(), "en"))
                            .superClasses(superClassesOf(cimClass, slice))
                            .comment(commentOf(cimClass.getDocumentation()))
                            .belongsToCategory(categories.get(cimClass.getPackageId()))
                            .stereotypes(
                                    XmiStereotypes.ofClass(
                                            cimClass.getStereotypes(),
                                            cimClass.isAbstract(),
                                            XmiStereotypes.isDatatype(cimClass.getStereotypes())))
                            .build());
            written.add(classId);
            writeAttributes(writer, cimClass);
            writeEnumEntries(writer, cimClass);
        }
        return written;
    }

    /** Only the superclasses the profile also holds; one outside it would point at nothing. */
    private List<RDFSSubClassOf> superClassesOf(XmiClass cimClass, Set<String> slice) {
        var superClasses = new ArrayList<RDFSSubClassOf>();
        for (var superClassId : cimClass.getSuperClassIds()) {
            var superClass = index.getClasses().get(superClassId);
            if (superClass != null && slice.contains(superClassId) && uris.canName(superClass)) {
                superClasses.add(
                        new RDFSSubClassOf(
                                new URI(uris.of(superClass)),
                                new RDFSLabel(superClass.getName(), "en")));
            }
        }
        return superClasses;
    }

    private void writeAttributes(ICIMModelWriter writer, XmiClass cimClass) {
        for (var attribute : cimClass.getAttributes()) {
            if (attribute.getName() == null
                    || attribute.getName().isBlank()
                    || !uris.canName(attribute.getStereotypes())) {
                continue;
            }
            writer.writeAttribute(
                    CIMAttribute.builder()
                            .uuid(UUID.randomUUID())
                            .uri(
                                    new URI(
                                            uris.of(
                                                    cimClass,
                                                    attribute.getName(),
                                                    attribute.getStereotypes())))
                            .label(new RDFSLabel(attribute.getName(), "en"))
                            .domain(
                                    new RDFSDomain(
                                            new URI(uris.of(cimClass)),
                                            new RDFSLabel(cimClass.getName(), "en")))
                            .multiplicity(
                                    XmiMultiplicities.ofBounds(
                                            attribute.getLowerBound(), attribute.getUpperBound()))
                            .dataType(dataTypes.of(attribute))
                            .comment(commentOf(attribute.getDocumentation()))
                            .fixedValue(fixedValueOf(attribute))
                            .defaultValue(defaultValueOf(attribute))
                            .stereotypes(XmiStereotypes.of(attribute.getStereotypes()))
                            .build());
        }
    }

    /**
     * Writes the entries of an enumeration, each typed by the enumeration itself. No stereotype:
     * being typed by its enumeration is what makes an entry one, and a release writes nothing else
     * about it beyond the label and the comment.
     */
    private void writeEnumEntries(ICIMModelWriter writer, XmiClass cimClass) {
        if (!cimClass.isEnumeration()) {
            return;
        }
        for (var literal : cimClass.getLiterals()) {
            if (literal.getName() == null || literal.getName().isBlank()) {
                continue;
            }
            writer.writeEnumEntry(
                    CIMEnumEntry.builder()
                            .uuid(UUID.randomUUID())
                            .uri(new URI(uris.of(cimClass, literal.getName(), Set.of())))
                            .type(
                                    new RDFType(
                                            new URI(uris.of(cimClass)),
                                            new RDFSLabel(cimClass.getName(), "en")))
                            .label(new RDFSLabel(literal.getName(), "en"))
                            .comment(commentOf(literal.getDocumentation()))
                            .build());
        }
    }

    /**
     * Writes both halves of every association the profile holds on both sides.
     *
     * <p>An end describes the half the class at the <em>other</em> end owns, so the two ends of a
     * connector produce one pair between them. A pair is only written once both of its classes are
     * in the profile: a half whose range is outside it would point at a resource the graph does not
     * contain.
     */
    private void writeAssociations(ICIMModelWriter writer, Set<String> written) {
        var ends = index.getAssociationEnds();
        for (var i = 0; i < ends.size(); i++) {
            var end = ends.get(i);
            if (!isWritable(end, written)) {
                continue;
            }
            var opposite = oppositeOf(ends, end);
            if (opposite == null || ends.indexOf(opposite) < i) {
                continue;
            }
            writer.writeAssociationPair(
                    new CIMAssociationPair(halfOf(end, opposite), halfOf(opposite, end)));
        }
    }

    /**
     * The half an end describes: owned by the class at the opposite end, ranged at this one, named
     * by this end's role. Reading the cross-over backwards points every association the wrong way.
     */
    private CIMAssociation halfOf(XmiAssociationEnd end, XmiAssociationEnd opposite) {
        var range = index.getClasses().get(end.getClassId());
        var domain = index.getClasses().get(end.getOppositeClassId());
        return CIMAssociation.builder()
                .uuid(UUID.randomUUID())
                .uri(
                        new URI(
                                uris.ofAssociationHalf(
                                        domain, end.getRoleName(), end.getStereotypes())))
                .label(new RDFSLabel(end.getRoleName(), "en"))
                .domain(
                        new RDFSDomain(
                                new URI(uris.of(domain)), new RDFSLabel(domain.getName(), "en")))
                .range(new RDFSRange(new URI(uris.of(range)), new RDFSLabel(range.getName(), "en")))
                .multiplicity(XmiMultiplicities.ofEnd(end.getMultiplicity()))
                .associationUsed(new CIMSAssociationUsed(end.isNavigable() ? "Yes" : "No"))
                .inverseRoleName(
                        new CIMSInverseRoleName(
                                uris.ofAssociationHalf(
                                        range, opposite.getRoleName(), opposite.getStereotypes())))
                .comment(commentOf(end.getDocumentation()))
                .stereotypes(stereotypesOf(end, opposite))
                .build();
    }

    /**
     * The stereotypes of a half: the ones applied to the association itself, plus {@code
     * ofAggregate} where the <em>opposite</em> end states the aggregation — the aggregate marker
     * belongs to the half across from the end carrying it. None of them is read back yet; see
     * {@link ICIMModelWriter}.
     */
    private List<CIMSStereotype> stereotypesOf(XmiAssociationEnd end, XmiAssociationEnd opposite) {
        var stereotypes = new ArrayList<>(XmiStereotypes.of(end.getStereotypes()));
        var aggregation = opposite.getAggregation();
        if ("shared".equals(aggregation) || "composite".equals(aggregation)) {
            stereotypes.add(new CIMSStereotype(XmiStereotypes.OF_AGGREGATE));
        }
        return stereotypes;
    }

    private boolean isWritable(XmiAssociationEnd end, Set<String> written) {
        return end.getRoleName() != null
                && !end.getRoleName().isBlank()
                && end.getOppositeRoleName() != null
                && !end.getOppositeRoleName().isBlank()
                && written.contains(end.getClassId())
                && written.contains(end.getOppositeClassId());
    }

    private XmiAssociationEnd oppositeOf(List<XmiAssociationEnd> ends, XmiAssociationEnd end) {
        for (var candidate : ends) {
            if (candidate != end && candidate.getConnectorId().equals(end.getConnectorId())) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * A fixed or default value, verbatim. One CGMES 2.4.15 profile URI is stated with a leading
     * space and its release keeps it, so the value is not tidied on the way through — the stripping
     * belongs where such a value becomes a URI, which cannot hold one, and not here where it stays
     * a literal.
     */
    private CIMSIsFixed fixedValueOf(XmiAttribute attribute) {
        return attribute.isFixed() && hasValue(attribute)
                ? new CIMSIsFixed(attribute.getDefaultValue())
                : null;
    }

    private CIMSIsDefault defaultValueOf(XmiAttribute attribute) {
        return !attribute.isFixed() && hasValue(attribute)
                ? new CIMSIsDefault(attribute.getDefaultValue())
                : null;
    }

    private boolean hasValue(XmiAttribute attribute) {
        return attribute.getDefaultValue() != null && !attribute.getDefaultValue().isBlank();
    }

    /**
     * The documentation as a comment. An export carries no language and no datatype for it, so the
     * format is the one the normalising form uses; a comment built without one cannot be written.
     */
    private RDFSComment commentOf(String documentation) {
        return documentation == null || documentation.isBlank()
                ? null
                : new RDFSComment(documentation, new URI(XSD.xstring.getURI()));
    }
}
