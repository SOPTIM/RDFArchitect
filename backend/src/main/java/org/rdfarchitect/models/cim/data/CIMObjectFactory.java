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

package org.rdfarchitect.models.cim.data;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.rdfarchitect.models.cim.CIMQuerySolutionParser;
import org.rdfarchitect.models.cim.data.dto.CIMAssociation;
import org.rdfarchitect.models.cim.data.dto.CIMAssociationPair;
import org.rdfarchitect.models.cim.data.dto.CIMAttribute;
import org.rdfarchitect.models.cim.data.dto.CIMClass;
import org.rdfarchitect.models.cim.data.dto.CIMEnumEntry;
import org.rdfarchitect.models.cim.data.dto.CIMPackage;
import org.rdfarchitect.models.cim.data.dto.relations.CIMSStereotype;
import org.rdfarchitect.models.cim.data.dto.relations.RDFSLabel;
import org.rdfarchitect.models.cim.data.dto.relations.datatype.CIMSDataType;
import org.rdfarchitect.models.cim.queries.CIMQueryVars;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Factory class that provides static methods for creating CIMObjects from queries
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CIMObjectFactory {

    /**
     * Generic method to create a list of objects from a ResultSet.
     *
     * @param resultSet The result set to process.
     * @param creator   A function that maps a {@link QuerySolution} to an object of type <T>.
     * @param <T>       The type of object to create.
     *
     * @return A list of created objects, each of type <T>.
     */
    private static <T> List<T> createObjectList(ResultSet resultSet, Function<QuerySolution, T> creator) {
        List<T> objectList = new ArrayList<>();
        while (resultSet.hasNext()) {
            objectList.add(creator.apply(resultSet.next()));
        }
        return objectList;
    }

    /**
     * Creates a {@link CIMClass} .
     *
     * @param classQuerySolution {@link QuerySolution} with results bound to variables from {@link CIMQueryVars CIMQueryVars}.
     *
     * @return {@link CIMClass}
     */
    public static CIMClass createCIMClass(QuerySolution classQuerySolution) {
        var parser = new CIMQuerySolutionParser(classQuerySolution);
        var uri = parser.getURI(CIMQueryVars.URI);
        var label = parser.getLabel(CIMQueryVars.LABEL);
        label = label != null ? label : new RDFSLabel(uri.getSuffix());
        return CIMClass.builder()
                       .uuid(parser.getUUID(CIMQueryVars.UUID))
                       .uri(uri)
                       .label(label)
                       .superClass(parser.getSubClassOf(CIMQueryVars.SUPER_CLASS_URI, CIMQueryVars.SUPER_CLASS_LABEL))
                       .comment(parser.getComment(CIMQueryVars.COMMENT))
                       .belongsToCategory(parser.getBelongsToCategory(CIMQueryVars.PACKAGE_URI, CIMQueryVars.PACKAGE_LABEL, CIMQueryVars.PACKAGE_UUID))
                       .build();
    }

    /**
     * Creates a {@link CIMAttribute} from a given query solution.
     *
     * @param querySolution The query solution to create the attribute from.
     *
     * @return The created attribute.
     */
    public static CIMAttribute createCIMAttribute(QuerySolution querySolution) {
        var parser = new CIMQuerySolutionParser(querySolution);
        CIMSDataType dataType = parser.getPrimitiveDataType(CIMQueryVars.DATA_TYPE_URI, CIMQueryVars.DATA_TYPE_LABEL);
        if (dataType == null) {
            dataType = parser.getRange(CIMQueryVars.RANGE_URI, CIMQueryVars.RANGE_LABEL);
        }
        return CIMAttribute.builder()
                           .uuid(parser.getUUID(CIMQueryVars.UUID))
                           .uri(parser.getURI(CIMQueryVars.URI))
                           .label(parser.getLabel(CIMQueryVars.LABEL))
                           .domain(parser.getDomain(CIMQueryVars.DOMAIN_URI, CIMQueryVars.DOMAIN_LABEL))
                           .multiplicity(parser.getMultiplicity(CIMQueryVars.MULTIPLICITY))
                           .dataType(dataType)
                           .stereotype(parser.getStereotype(CIMQueryVars.STEREOTYPE))
                           .comment(parser.getComment(CIMQueryVars.COMMENT))
                           .fixedValue(parser.getIsFixed(CIMQueryVars.IS_FIXED))
                           .defaultValue(parser.getIsDefault(CIMQueryVars.IS_DEFAULT))
                           .build();
    }

    /**
     * Creates a List of {@link CIMAttribute CIMAttributes} .
     *
     * @param attributeResultSet {@link ResultSet} with results bound to variables from {@link CIMQueryVars CIMQueryVars}.
     *
     * @return a list containing {@link CIMAttribute CIMAttributes}.
     */
    public static List<CIMAttribute> createCIMAttributeList(ResultSet attributeResultSet) {
        return createObjectList(attributeResultSet, CIMObjectFactory::createCIMAttribute);
    }

    /**
     * Creates a {@link CIMAssociation} from a given query solution.
     *
     * @param associationQuerySolution The query solution to create the association from.
     *
     * @return The created association.
     */
    public static CIMAssociation createCIMAssociation(QuerySolution associationQuerySolution) {
        var parser = new CIMQuerySolutionParser(associationQuerySolution);
        return CIMAssociation.builder()
                             .uuid(parser.getUUID(CIMQueryVars.UUID))
                             .uri(parser.getURI(CIMQueryVars.URI))
                             .label(parser.getLabel(CIMQueryVars.LABEL))
                             .multiplicity(parser.getMultiplicity(CIMQueryVars.MULTIPLICITY))
                             .associationUsed(parser.getAssociationUsed(CIMQueryVars.ASSOCIATION_USED))
                             .comment(parser.getComment(CIMQueryVars.COMMENT))
                             .domain(parser.getDomain(CIMQueryVars.DOMAIN_URI, CIMQueryVars.DOMAIN_LABEL))
                             .range(parser.getRange(CIMQueryVars.RANGE_URI, CIMQueryVars.RANGE_LABEL))
                             .inverseRoleName(parser.getInverseRoleName(CIMQueryVars.INVERSE_ROLE_NAME))
                             .build();
    }

    /**
     * Creates a {@link CIMAssociation} from a given query solution. Uses inverse QueryVars to create the association.
     *
     * @param associationQuerySolution The query solution to create the association from.
     *
     * @return The created association.
     */
    public static CIMAssociation createInverseCIMAssociation(QuerySolution associationQuerySolution) {
        var parser = new CIMQuerySolutionParser(associationQuerySolution);
        return CIMAssociation.builder()
                             .uuid(parser.getUUID(CIMQueryVars.UUID))
                             .uri(parser.getURI(CIMQueryVars.INVERSE_ROLE_NAME))
                             .label(parser.getLabel(CIMQueryVars.Inverse.LABEL))
                             .multiplicity(parser.getMultiplicity(CIMQueryVars.Inverse.MULTIPLICITY))
                             .associationUsed(parser.getAssociationUsed(CIMQueryVars.Inverse.ASSOCIATION_USED))
                             .comment(parser.getComment(CIMQueryVars.Inverse.COMMENT))
                             .inverseRoleName(parser.getInverseRoleName(CIMQueryVars.URI))
                             .range(parser.getRange(CIMQueryVars.DOMAIN_URI, CIMQueryVars.DOMAIN_LABEL))
                             .domain(parser.getDomain(CIMQueryVars.RANGE_URI, CIMQueryVars.RANGE_LABEL))
                             .build();
    }

    /**
     * Creates a List of {@link CIMAssociation CIMAssociations} .
     *
     * @param associationResultSet {@link ResultSet} with results bound to variables from {@link CIMQueryVars CIMQueryVars}.
     *
     * @return a list containing {@link CIMAssociation CIMAssociations}.
     */
    public static List<CIMAssociation> createCIMAssociationList(ResultSet associationResultSet) {
        return createObjectList(associationResultSet, CIMObjectFactory::createCIMAssociation);
    }

    /**
     * Creates a {@link CIMAssociationPair} from a given query solution.
     *
     * @param associationPairQuerySolution The query solution to create the association pair from.
     *
     * @return The created association pair.
     */
    public static CIMAssociationPair createCIMAssociationPair(QuerySolution associationPairQuerySolution) {
        var from = createCIMAssociation(associationPairQuerySolution);
        var to = createInverseCIMAssociation(associationPairQuerySolution);
        return new CIMAssociationPair(from, to);
    }

    /**
     * Creates a List of {@link CIMAssociation CIMAssociations} .
     *
     * @param associationPairResultSet {@link ResultSet} with results bound to variables from {@link CIMQueryVars CIMQueryVars}.
     *
     * @return a list containing {@link CIMAssociation CIMAssociations}.
     */
    public static List<CIMAssociationPair> createCIMAssociationPairList(ResultSet associationPairResultSet) {
        return createObjectList(associationPairResultSet, CIMObjectFactory::createCIMAssociationPair);
    }

    /**
     * Creates a {@link CIMPackage} from a given query solution.
     *
     * @param packageQuerySolution The query solution to create the package from.
     *
     * @return The created package.
     */
    public static CIMPackage createCIMPackage(QuerySolution packageQuerySolution) {
        var parser = new CIMQuerySolutionParser(packageQuerySolution);
        return CIMPackage.builder()
                         .uuid(parser.getUUID(CIMQueryVars.UUID))
                         .uri(parser.getURI(CIMQueryVars.URI))
                         .label(parser.getLabel(CIMQueryVars.LABEL))
                         .comment(parser.getComment(CIMQueryVars.COMMENT))
                         .belongsToCategory(parser.getBelongsToCategory(CIMQueryVars.PACKAGE_URI, CIMQueryVars.PACKAGE_LABEL, CIMQueryVars.PACKAGE_UUID))
                         .build();
    }

    /**
     * Creates a List of {@link CIMPackage CIMPackages} .
     *
     * @param packageResultSet {@link ResultSet} with results bound to variables from {@link CIMQueryVars CIMQueryVars}.
     *
     * @return a list containing {@link CIMPackage CIMPackages}.
     */
    public static List<CIMPackage> createCIMPackageList(ResultSet packageResultSet) {
        return createObjectList(packageResultSet, CIMObjectFactory::createCIMPackage);
    }

    /**
     * Creates a {@link CIMSStereotype} from a given query solution.
     *
     * @param stereotypeQuerySolution The query solution to create the stereotype from.
     *
     * @return The created stereotype.
     */
    public static CIMSStereotype createCIMSStereotype(QuerySolution stereotypeQuerySolution) {
        var parser = new CIMQuerySolutionParser(stereotypeQuerySolution);
        return new CIMSStereotype(parser.getStereotype(CIMQueryVars.STEREOTYPE).toString());
    }

    /**
     * Creates a List of external {@link CIMPackage CIMPackages} .
     *
     * @param externalPackageResultSet {@link ResultSet} with results bound to {@link CIMQueryVars#URI},
     *                                 {@link CIMQueryVars#LABEL}, {@link CIMQueryVars#COMMENT}, {@link CIMQueryVars#PACKAGE_URI} and {@link CIMQueryVars#PACKAGE_LABEL}.
     *
     * @return a list containing external {@link CIMPackage CIMPackage}.
     */
    public static List<CIMPackage> createExternalCIMPackageList(ResultSet externalPackageResultSet) {
        List<CIMPackage> externalPackageObjectList = new ArrayList<>();
        while (externalPackageResultSet.hasNext()) {
            var parser = new CIMQuerySolutionParser(externalPackageResultSet.next());
            var uri = parser.getURI(CIMQueryVars.URI);
            var packageObject = CIMPackage.builder()
                                          .uuid(parser.getUUID(CIMQueryVars.UUID))
                                          .uri(uri)
                                          .label(new RDFSLabel(uri.getSuffix().replace("Package_", "")))
                                          .build();
            externalPackageObjectList.add(packageObject);
        }
        return externalPackageObjectList;
    }

    /**
     * Creates a List of {@link CIMSStereotype CIMSStereotypes} .
     *
     * @param stereotypeResultSet {@link ResultSet} with results bound to {@link CIMQueryVars}.
     *
     * @return a list containing {@link CIMSStereotype CIMSStereotypes}.
     */
    public static List<CIMSStereotype> createCIMStereotypeList(ResultSet stereotypeResultSet) {
        return createObjectList(stereotypeResultSet, CIMObjectFactory::createCIMSStereotype);
    }

    /**
     * Creates a {@link CIMEnumEntry} from a query solution.
     *
     * @param enumEntryQuerySolution The query solution to create the enum entry from.
     *
     * @return The created enum entry.
     */
    public static CIMEnumEntry createCIMEnumEntry(QuerySolution enumEntryQuerySolution) {
        var parser = new CIMQuerySolutionParser(enumEntryQuerySolution);
        return CIMEnumEntry.builder()
                           .uuid(parser.getUUID(CIMQueryVars.UUID))
                           .uri(parser.getURI(CIMQueryVars.URI))
                           .type(parser.getType(CIMQueryVars.TYPE_URI, CIMQueryVars.TYPE_LABEL))
                           .label(parser.getLabel(CIMQueryVars.LABEL))
                           .comment(parser.getComment(CIMQueryVars.COMMENT))
                           .stereotype(parser.getStereotype(CIMQueryVars.STEREOTYPE))
                           .build();
    }

    /**
     * Creates a List of {@link CIMEnumEntry CIMEnumEntries} .
     *
     * @param enumEntriesResultSet {@link ResultSet} with results bound to {@link CIMQueryVars}.
     *
     * @return a list containing {@link CIMEnumEntry CIMEnumEntries}.
     */
    public static List<CIMEnumEntry> createCIMEnumEntryList(ResultSet enumEntriesResultSet) {
        return createObjectList(enumEntriesResultSet, CIMObjectFactory::createCIMEnumEntry);
    }
}