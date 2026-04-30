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

package org.rdfarchitect.models.cim;

import lombok.RequiredArgsConstructor;

import org.apache.jena.graph.Node;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSAssociationUsed;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSBelongsToCategory;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSInverseRoleName;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSIsDefault;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSIsFixed;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSMultiplicity;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSStereotype;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSComment;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSDomain;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSSubClassOf;
import org.rdfarchitect.models.cim.data.dto.relations.RDFType;
import org.rdfarchitect.models.cim.data.dto.relations.datatype.CIMSPrimitiveDataType;
import org.rdfarchitect.models.cim.data.dto.relations.datatype.RDFSRange;
import org.rdfarchitect.models.cim.data.dto.relations.uri.URI;

import java.util.AbstractMap;
import java.util.UUID;

/**
 * Parses a {@link QuerySolution} to extract the values of the variables used in the context of CIM.
 */
@RequiredArgsConstructor
public class CIMQuerySolutionParser {

    private final QuerySolution qs;

    /**
     * Helper record to store a URI, Label pair
     *
     * @param uri The URI
     * @param label The Label
     */
    private record URILabelPair(URI uri, RDFSLabel label) {}

    /**
     * Extracts the {@link URILabelPair} from the query solution.
     *
     * @param uriVar The variable name of the URI.
     * @param labelVar The variable name of the label.
     * @return The URI and label as a {@link URILabelPair}. The label can be null.
     */
    private URILabelPair getURILabelPair(String uriVar, String labelVar) {
        var uriNode = qs.get(uriVar).asNode();
        URI uri = new URI(uriNode.getURI());
        RDFSLabel label = null;
        if (qs.contains(labelVar)) {
            var labelNode = qs.get(labelVar).asNode();
            label =
                    new RDFSLabel(
                            labelNode.getLiteralLexicalForm(), labelNode.getLiteralLanguage());
        }
        if (label == null) {
            label = new RDFSLabel(uri.getSuffix());
        }
        return new URILabelPair(uri, label);
    }

    /**
     * Extracts the {@link CIMSPrimitiveDataType} from the query solution.
     *
     * @param primitiveTypeUriVar The variable name of the primitive datatype URI.
     * @param primitiveTypeLabelVar The variable name of the primitive datatype label.
     * @return The primitive datatype or null, if the given variables don't exist in the solution.
     */
    public CIMSPrimitiveDataType getPrimitiveDataType(
            String primitiveTypeUriVar, String primitiveTypeLabelVar) {
        if (!qs.contains(primitiveTypeUriVar)) {
            return null;
        }
        var uRILabelPair = getURILabelPair(primitiveTypeUriVar, primitiveTypeLabelVar);
        return new CIMSPrimitiveDataType(uRILabelPair.uri, uRILabelPair.label);
    }

    /**
     * Extracts the {@link RDFSRange} from the query solution.
     *
     * @param rangeUriVar The variable name of the range URI.
     * @param rangeLabelVar The variable name of the range label.
     * @return The range or null, if the given variables don't exist in the solution.
     */
    public RDFSRange getRange(String rangeUriVar, String rangeLabelVar) {
        if (!qs.contains(rangeUriVar)) {
            return null;
        }
        var uRILabelPair = getURILabelPair(rangeUriVar, rangeLabelVar);
        return new RDFSRange(uRILabelPair.uri, uRILabelPair.label);
    }

    /**
     * Extracts the {@link CIMSAssociationUsed} used from the query solution.
     *
     * @param associationUsedVar The variable name of the association used.
     * @return The association used or null, if the given variables doesn't exist in the solution.
     */
    public CIMSAssociationUsed getAssociationUsed(String associationUsedVar) {
        if (!qs.contains(associationUsedVar)) {
            return null;
        }
        Node associationUsedNode = qs.get(associationUsedVar).asNode();
        return new CIMSAssociationUsed(associationUsedNode.getLiteralLexicalForm());
    }

    /**
     * Extracts the {@link CIMSBelongsToCategory} from the query solution.
     *
     * @param packageUriVar The variable name of the package URI.
     * @param packageLabelVar The variable name of the package label.
     * @param packageUUIDVar The variable name of the package UUID.
     * @return The belongs to category or null, if the given variables don't exist in the solution.
     */
    public CIMSBelongsToCategory getBelongsToCategory(
            String packageUriVar, String packageLabelVar, String packageUUIDVar) {
        if (!qs.contains(packageUriVar)) {
            return null;
        }
        var uriLabelPair = getURILabelPair(packageUriVar, packageLabelVar);
        var uriLabel = uriLabelPair.label;
        if (uriLabel == null) {
            uriLabel = new RDFSLabel(uriLabelPair.uri.getSuffix());
        }
        return new CIMSBelongsToCategory(uriLabelPair.uri, uriLabel, getUUID(packageUUIDVar));
    }

    /**
     * Extracts the {@link CIMSInverseRoleName} from the query solution.
     *
     * @param inverseRoleNameVar The variable name of the inverse role name.
     * @return The inverse role name or null, if the given variables doesn't exist in the solution.
     */
    public CIMSInverseRoleName getInverseRoleName(String inverseRoleNameVar) {
        if (!qs.contains(inverseRoleNameVar)) {
            return null;
        }
        var uriNode = qs.get(inverseRoleNameVar).asNode();
        return new CIMSInverseRoleName(uriNode.getURI());
    }

    /**
     * Extracts the {@link CIMSIsDefault} from the query solution.
     *
     * @param isDefaultVar The variable name of the isDefault.
     * @return The is default or null, if the given variables doesn't exist in the solution.
     */
    public CIMSIsDefault getIsDefault(String isDefaultVar) {
        if (!qs.contains(isDefaultVar)) {
            return null;
        }
        var isDefaultRDFNode = qs.get(isDefaultVar);
        var tuple = getValueDatatypePair(isDefaultRDFNode);
        return new CIMSIsDefault(tuple.getKey(), tuple.getValue());
    }

    /**
     * Extracts the {@link CIMSIsFixed} from the query solution.
     *
     * @param isFixedVar The variable name of the isFixed.
     * @return The is fixed or null, if the given variables doesn't exist in the solution.
     */
    public CIMSIsFixed getIsFixed(String isFixedVar) {
        if (!qs.contains(isFixedVar)) {
            return null;
        }
        var isFixedRDFNode = qs.get(isFixedVar);
        var tuple = getValueDatatypePair(isFixedRDFNode);
        return new CIMSIsFixed(tuple.getKey(), tuple.getValue());
    }

    /**
     * Helper method to extract the value and datatype of a RDFNode.
     *
     * @param node The RDFNode to extract the value and datatype from.
     * @return A {@link AbstractMap.SimpleEntry} with the value as key and the datatype as value.
     */
    private AbstractMap.SimpleEntry<String, URI> getValueDatatypePair(RDFNode node) {
        URI datatype = null;
        String value = null;
        if (node.isLiteral()) {
            value = node.asNode().getLiteralLexicalForm();
            var dataTypeUri = node.asNode().getLiteralDatatypeURI();
            datatype = dataTypeUri.isEmpty() ? null : new URI(dataTypeUri);
        }
        var tuple = getPredicateObjectPair(node);
        value = tuple.getKey() != null ? tuple.getKey() : value;
        datatype = tuple.getValue() != null ? tuple.getValue() : datatype;
        return new AbstractMap.SimpleEntry<>(value, datatype);
    }

    /**
     * Helper method to extract the first predicate and object of a blankNode.
     *
     * @param node The RDFNode to extract the predicate and object from.
     * @return A {@link AbstractMap.SimpleEntry} with the predicate as key and the object as value.
     */
    private AbstractMap.SimpleEntry<String, URI> getPredicateObjectPair(RDFNode node) {
        URI datatype = null;
        String value = null;
        if (node.isAnon()) {
            var it = ((Resource) node).listProperties();
            if (it.hasNext()) {
                var stmt = it.next().asTriple();
                value = stmt.getObject().toString();
                datatype = new URI(stmt.getPredicate().toString());
            }
        }
        return new AbstractMap.SimpleEntry<>(value, datatype);
    }

    /**
     * Extracts the {@link CIMSMultiplicity} from the query solution.
     *
     * @param multiplicityVar The variable name of the multiplicity.
     * @return The multiplicity or null, if the given variables doesn't exist in the solution.
     */
    public CIMSMultiplicity getMultiplicity(String multiplicityVar) {
        if (!qs.contains(multiplicityVar)) {
            return null;
        }
        var uriNode = qs.get(multiplicityVar).asNode();
        return new CIMSMultiplicity(uriNode.getURI());
    }

    /**
     * Extracts the {@link CIMSStereotype} from the query solution.
     *
     * @param stereotypeVar The variable name of the stereotype.
     * @return The stereotype or null, if the given variables doesn't exist in the solution.
     */
    public CIMSStereotype getStereotype(String stereotypeVar) {
        if (!qs.contains(stereotypeVar)) {
            return null;
        }
        Node stereotypeNode = qs.get(stereotypeVar).asNode();
        if (stereotypeNode.isLiteral()) {
            return new CIMSStereotype(stereotypeNode.getLiteralLexicalForm());
        } else if (stereotypeNode.isURI()) {
            return new CIMSStereotype(stereotypeNode.getURI());
        }
        return null;
    }

    /**
     * Extracts the {@link RDFSComment} from the query solution.
     *
     * @param commentVar The variable name of the comment.
     * @return The comment or null, if the given variables doesn't exist in the solution.
     */
    public RDFSComment getComment(String commentVar) {
        if (!qs.contains(commentVar)) {
            return null;
        }
        var comment = qs.get(commentVar).asNode();
        return new RDFSComment(
                comment.getLiteralLexicalForm(), new URI(comment.getLiteralDatatypeURI()));
    }

    /**
     * Extracts the {@link RDFSDomain} from the query solution.
     *
     * @param domainUriVar The variable name of the domain URI.
     * @param domainLabelVar The variable name of the domain label.
     * @return The domain or null, if the given variables don't exist in the solution.
     */
    public RDFSDomain getDomain(String domainUriVar, String domainLabelVar) {
        if (!qs.contains(domainUriVar)) {
            return null;
        }
        var uRILabelPair = getURILabelPair(domainUriVar, domainLabelVar);
        return new RDFSDomain(uRILabelPair.uri, uRILabelPair.label);
    }

    public UUID getDomainUUID(String domainUUIDVar) {
        if (!qs.contains(domainUUIDVar)) {
            return null;
        }
        var domainUUIDNode = qs.get(domainUUIDVar).asNode();
        return UUID.fromString(domainUUIDNode.getLiteralLexicalForm());
    }

    /**
     * Extracts the {@link RDFSLabel} from the query solution.
     *
     * @param labelVar The variable name of the label.
     * @return The label or null, if the given variables doesn't exist in the solution.
     */
    public RDFSLabel getLabel(String labelVar) {
        if (!qs.contains(labelVar)) {
            return null;
        }
        var labelNode = qs.get(labelVar).asNode();
        return new RDFSLabel(labelNode.getLiteralLexicalForm(), labelNode.getLiteralLanguage());
    }

    /**
     * Extracts the {@link RDFSSubClassOf} from the query solution.
     *
     * @param superClassUriVar The variable name of the super class URI.
     * @param superClassLabelVar The variable name of the super class label.
     * @return The super class or null, if the given variables don't exist
     */
    public RDFSSubClassOf getSubClassOf(String superClassUriVar, String superClassLabelVar) {
        if (!qs.contains(superClassUriVar)) {
            return null;
        }
        var uRILabelPair = getURILabelPair(superClassUriVar, superClassLabelVar);
        return new RDFSSubClassOf(uRILabelPair.uri, uRILabelPair.label);
    }

    /**
     * Extracts the {@link RDFType} from the query solution.
     *
     * @param typeUriVar The variable name of the type URI.
     * @param typeLabelVar The variable name of the type label.
     * @return The type or null, if the given variables don't exist in the solution.
     */
    public RDFType getType(String typeUriVar, String typeLabelVar) {
        if (!qs.contains(typeUriVar)) {
            return null;
        }
        var uRILabelPair = getURILabelPair(typeUriVar, typeLabelVar);
        return new RDFType(uRILabelPair.uri, uRILabelPair.label);
    }

    /**
     * Extracts the {@link URI} from the query solution.
     *
     * @param uriVar The variable name of the URI.
     * @return The URI or null, if the given variables doesn't exist in the solution.
     */
    public URI getURI(String uriVar) {
        if (!qs.contains(uriVar)) {
            return null;
        }
        var uriNode = qs.get(uriVar).asNode();
        return new URI(uriNode.getURI());
    }

    public UUID getUUID(String uuidVar) {
        if (!qs.contains(uuidVar)) {
            return null;
        }
        var uuidNode = qs.get(uuidVar).asNode();
        return UUID.fromString(uuidNode.getLiteralLexicalForm());
    }
}
