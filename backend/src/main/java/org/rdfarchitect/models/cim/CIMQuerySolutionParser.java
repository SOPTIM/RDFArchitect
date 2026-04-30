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
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.RDFNode;
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
            String uriLabelString = uriLabelPair.uri.getSuffix().replace("Package_", "");
            uriLabel = new RDFSLabel(uriLabelString);
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
     * Extracts the {@link CIMSIsDefault} from the query solution. The outer variable is bound
     * either directly to the literal or to a blank-node wrapper; in the blank-node case the inner
     * variable holds the wrapper's {@code rdfs:Literal} object (see {@link
     * org.rdfarchitect.models.cim.queries.select.CIMQueryBuilder#appendIsDefaultQuery}).
     *
     * @param isDefaultVar Outer variable name (literal or blank node).
     * @param isDefaultInnerVar Inner variable name (literal inside the blank-node wrapper).
     * @return The is default or {@code null} when the outer variable is not bound.
     */
    public CIMSIsDefault getIsDefault(String isDefaultVar, String isDefaultInnerVar) {
        var parsed = parseValueNode(isDefaultVar, isDefaultInnerVar);
        if (parsed == null) {
            return null;
        }
        return new CIMSIsDefault(parsed.value(), parsed.dataType(), parsed.blankNode());
    }

    /**
     * Extracts the {@link CIMSIsFixed} from the query solution. The outer variable is bound either
     * directly to the literal or to a blank-node wrapper; in the blank-node case the inner variable
     * holds the wrapper's {@code rdfs:Literal} object (see {@link
     * org.rdfarchitect.models.cim.queries.select.CIMQueryBuilder#appendIsFixedQuery}).
     *
     * @param isFixedVar Outer variable name (literal or blank node).
     * @param isFixedInnerVar Inner variable name (literal inside the blank-node wrapper).
     * @return The is fixed or {@code null} when the outer variable is not bound.
     */
    public CIMSIsFixed getIsFixed(String isFixedVar, String isFixedInnerVar) {
        var parsed = parseValueNode(isFixedVar, isFixedInnerVar);
        if (parsed == null) {
            return null;
        }
        return new CIMSIsFixed(parsed.value(), parsed.dataType(), parsed.blankNode());
    }

    private record ParsedValueNode(String value, URI dataType, boolean blankNode) {}

    /**
     * Resolves an attribute value-node from its outer/inner variable bindings:
     *
     * <ul>
     *   <li>outer literal → direct shape, datatype taken from the literal,
     *   <li>outer blank node + bound inner literal → blank-node shape, datatype from the inner
     *       literal,
     *   <li>outer blank node without inner literal → ignored (malformed wrapper),
     *   <li>outer not bound → {@code null}.
     * </ul>
     */
    private ParsedValueNode parseValueNode(String outerVar, String innerVar) {
        if (!qs.contains(outerVar)) {
            return null;
        }
        RDFNode outer = qs.get(outerVar);
        if (outer.isLiteral()) {
            return fromLiteral(outer.asLiteral(), false);
        }
        if (outer.isAnon() && qs.contains(innerVar) && qs.get(innerVar).isLiteral()) {
            return fromLiteral(qs.get(innerVar).asLiteral(), true);
        }
        return null;
    }

    private ParsedValueNode fromLiteral(Literal literal, boolean blankNode) {
        var dataTypeUri = literal.getDatatypeURI();
        var dataType = dataTypeUri == null || dataTypeUri.isEmpty() ? null : new URI(dataTypeUri);
        return new ParsedValueNode(literal.getLexicalForm(), dataType, blankNode);
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
