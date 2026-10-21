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

package org.rdfarchitect.models.cim.data.dto.facade;

import lombok.RequiredArgsConstructor;

import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.api.dto.ontology.OntologyDTO;
import org.rdfarchitect.context.UserSettingsContext;
import org.rdfarchitect.models.cim.data.dto.CIMAssociation;
import org.rdfarchitect.models.cim.data.dto.CIMAssociationPair;
import org.rdfarchitect.models.cim.data.dto.CIMAttribute;
import org.rdfarchitect.models.cim.data.dto.CIMClass;
import org.rdfarchitect.models.cim.data.dto.CIMEnumEntry;
import org.rdfarchitect.models.cim.data.dto.CIMPackage;
import org.rdfarchitect.models.cim.data.dto.relations.AttributeValueNode;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSStereotype;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSComment;
import org.rdfarchitect.models.cim.data.dto.relations.datatype.CIMSDataType;
import org.rdfarchitect.models.cim.ontology.OntologyFacade;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.models.cim.rdf.resources.CIMStereotypes;
import org.rdfarchitect.models.cim.rdf.resources.RDFA;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.List;

/**
 * Writes a CIM profile graph into a {@link Model}.
 *
 * <p>Through the model rather than through SPARQL, which is what {@link
 * org.rdfarchitect.models.cim.queries.update.CIMUpdates} does and what the house style otherwise
 * is. Two reasons it is different here: there is no store to run an update against — the caller
 * builds a model and hands it over whole — and an update is for changing data under a pattern,
 * while this creates from nothing and has no {@code WHERE} to match. The volume settles it: a full
 * CGMES 3.0 profile is thousands of classes and thousands of association halves.
 *
 * <p>The rules about what a triple looks like are the same ones {@code CIMUpdates} applies, and
 * {@code CIMModelWriterMatchesCIMUpdatesTest} is what holds the two together. The one that has to
 * be copied rather than called is the choice between an IRI and a plain literal: {@link
 * org.rdfarchitect.rdf.RDFUtils#wrapURLorLiteral} decides it the same way but returns SPARQL text,
 * which a model cannot take.
 *
 * @see ICIMModelWriter
 */
@RequiredArgsConstructor
public class CIMModelWriter implements ICIMModelWriter {

    private final String graphUri;

    private final Model model;

    @Override
    public void writePackage(CIMPackage cimPackage) {
        var resource = resourceOf(cimPackage.getUri());
        model.add(resource, RDF.type, CIMS.classCategory);
        writeIdentity(resource, cimPackage.getUuid(), cimPackage.getLabel());
        writeComment(resource, cimPackage.getComment());
        // The parent, which CIMUpdates.insertPackage does not write.
        if (cimPackage.getBelongsToCategory() != null) {
            model.add(
                    resource,
                    CIMS.belongsToCategory,
                    resourceOf(cimPackage.getBelongsToCategory().getUri()));
        }
    }

    @Override
    public void writeClass(CIMClass cimClass) {
        var resource = resourceOf(cimClass.getUri());
        model.add(resource, RDF.type, RDFS.Class);
        writeIdentity(resource, cimClass.getUuid(), cimClass.getLabel());
        for (var superClass : cimClass.getSuperClasses()) {
            model.add(resource, RDFS.subClassOf, resourceOf(superClass.getUri()));
        }
        writeComment(resource, cimClass.getComment());
        if (cimClass.getBelongsToCategory() != null) {
            model.add(
                    resource,
                    CIMS.belongsToCategory,
                    resourceOf(cimClass.getBelongsToCategory().getUri()));
        }
        writeStereotypes(resource, cimClass.getStereotypes());
    }

    @Override
    public void writeEnumEntry(CIMEnumEntry enumEntry) {
        var resource = resourceOf(enumEntry.getUri());
        // An entry is typed by its enumeration, not by rdfs:Class.
        model.add(resource, RDF.type, resourceOf(enumEntry.getType().getUri()));
        writeIdentity(resource, enumEntry.getUuid(), enumEntry.getLabel());
        writeComment(resource, enumEntry.getComment());
        if (enumEntry.getStereotype() != null) {
            writeStereotypes(resource, List.of(enumEntry.getStereotype()));
        }
    }

    @Override
    public void writeAttribute(CIMAttribute attribute) {
        var resource = resourceOf(attribute.getUri());
        model.add(resource, RDF.type, RDF.Property);
        writeIdentity(resource, attribute.getUuid(), attribute.getLabel());
        model.add(resource, RDFS.domain, resourceOf(attribute.getDomain().getUri()));
        model.add(resource, CIMS.multiplicity, resourceOf(attribute.getMultiplicity().getUri()));
        // Mandatory: a domain-bound property carrying neither this nor cims:AssociationUsed is
        // stored and never displayed.
        model.add(resource, CIMS.stereotype, CIMStereotypes.attribute);
        writeStereotypes(resource, attribute.getStereotypes());
        writeDataType(resource, attribute.getDataType());
        writeComment(resource, attribute.getComment());
        writeValueNode(resource, CIMS.isFixed, attribute.getFixedValue());
        writeValueNode(resource, CIMS.isDefault, attribute.getDefaultValue());
    }

    @Override
    public void writeAssociationPair(CIMAssociationPair pair) {
        writeAssociation(pair.getFrom());
        writeAssociation(pair.getTo());
    }

    @Override
    public void bindPrefix(String prefix, String namespace) {
        model.setNsPrefix(prefix, namespace);
    }

    @Override
    public void writeOntology(OntologyDTO ontology) {
        // Delegated rather than repeated: the header has its own writer already, and it is one
        // resource, so that it goes through SPARQL internally costs nothing.
        new OntologyFacade(model).createOntology(ontology);
    }

    @Override
    public Graph graph() {
        return model.getGraph();
    }

    /** The graph this writer fills, as the caller named it. */
    public String getGraphUri() {
        return graphUri;
    }

    // -------------------------------------------------------------------------

    private void writeAssociation(CIMAssociation association) {
        var resource = resourceOf(association.getUri());
        model.add(resource, RDF.type, RDF.Property);
        writeIdentity(resource, association.getUuid(), association.getLabel());
        model.add(resource, RDFS.domain, resourceOf(association.getDomain().getUri()));
        model.add(resource, RDFS.range, resourceOf(association.getRange().getUri()));
        model.add(resource, CIMS.associationUsed, association.getAssociationUsed().toString());
        model.add(
                resource,
                CIMS.inverseRoleName,
                resourceOf(association.getInverseRoleName().getUri()));
        model.add(resource, CIMS.multiplicity, resourceOf(association.getMultiplicity().getUri()));
        writeComment(resource, association.getComment());
        // Not written by CIMUpdates, so ofAggregate does not survive an edit; see ICIMModelWriter.
        writeStereotypes(resource, association.getStereotypes());
    }

    private void writeIdentity(
            Resource resource,
            java.util.UUID uuid,
            org.rdfarchitect.models.cim.data.dto.relations.RDFSLabel label) {
        if (uuid != null) {
            model.add(resource, RDFA.uuid, uuid.toString());
        }
        if (label != null) {
            model.add(resource, RDFS.label, label.asLangLiteral());
        }
    }

    private void writeComment(Resource resource, RDFSComment comment) {
        if (comment == null) {
            return;
        }
        model.add(
                resource,
                RDFS.comment,
                UserSettingsContext.get().normalizeComments()
                        ? comment.asTypedLiteral()
                        : comment.asLiteral());
    }

    private void writeStereotypes(Resource resource, List<CIMSStereotype> stereotypes) {
        if (stereotypes == null) {
            return;
        }
        for (var stereotype : stereotypes) {
            model.add(resource, CIMS.stereotype, nodeFor(stereotype.getStereotype()));
        }
    }

    /**
     * The datatype of an attribute, where the kind selects the predicate rather than labelling it:
     * a primitive becomes {@code cims:dataType} and everything else an {@code rdfs:range}.
     */
    private void writeDataType(Resource resource, CIMSDataType dataType) {
        if (dataType == null) {
            return;
        }
        var predicate =
                dataType.getType() == CIMSDataType.Type.PRIMITIVE ? CIMS.datatype : RDFS.range;
        model.add(resource, predicate, resourceOf(dataType.getUri()));
    }

    /**
     * A fixed or default value, either as a literal or wrapped in a blank node carrying it under
     * {@code rdfs:Literal}, which is the shape the value itself asks for.
     */
    private void writeValueNode(
            Resource resource,
            org.apache.jena.rdf.model.Property predicate,
            AttributeValueNode value) {
        if (value == null || value.getValue() == null) {
            return;
        }
        if (!value.isBlankNode()) {
            model.add(resource, predicate, value.asLiteral());
            return;
        }
        var wrapper = model.createResource();
        model.add(resource, predicate, wrapper);
        model.add(wrapper, model.createProperty(RDFS.Literal.getURI()), value.asLiteral());
    }

    private Resource resourceOf(org.rdfarchitect.models.cim.data.dto.relations.uri.URI uri) {
        return model.createResource(uri.toString());
    }

    /**
     * A value as an IRI when it is one and as a plain literal otherwise. The test is the one {@link
     * org.rdfarchitect.rdf.RDFUtils#wrapURLorLiteral} applies and has to stay the one it applies:
     * it demands a protocol a URL handler knows, so {@code ...UML#concrete} becomes an IRI while
     * {@code CIMDatatype} and an exotic scheme become literals. Getting this wrong breaks datatype
     * checks, SHACL and rendering without anything failing.
     */
    private RDFNode nodeFor(String value) {
        try {
            new java.net.URI(value).toURL();
            return model.createResource(value);
        } catch (URISyntaxException | MalformedURLException | IllegalArgumentException _) {
            return model.createLiteral(value);
        }
    }
}
