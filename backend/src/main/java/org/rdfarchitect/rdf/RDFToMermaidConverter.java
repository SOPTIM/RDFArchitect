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

package org.rdfarchitect.rdf;

import lombok.RequiredArgsConstructor;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.shared.impl.PrefixMappingImpl;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.rdfarchitect.cim.rdf.resources.CIMS;
import org.rdfarchitect.cim.rdf.resources.CIMStereotypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.unbescape.html.HtmlEscape;

import java.util.ArrayList;

@SuppressWarnings("java:S1192")
@RequiredArgsConstructor
public class RDFToMermaidConverter {

    private static final Logger logger = LoggerFactory.getLogger(RDFToMermaidConverter.class);

    private PrefixMapping prefixMapping;

    private final Model model;

    public static final boolean DISPLAY_COMMENT = false;
    public static final boolean REMOVE_LANG_TAG = true;
    public static final String CALL_BACK_FUNCTION_NAME = "getClassInformation";

    public static final String ONLY_ALPHANUMERICAL_REGEX = "[^a-zA-Z0-9]+";

    public static String convertToFlowchart(Model model) {
        return convertToFlowchart(model, "LR");
    }

    /**
     * Converts the model to a Mermaid Flowchart string
     *
     * @param model       contains the rdf triples
     * @param orientation LR, RL, TB, BT
     *
     * @return String in Mermaid-Flowchart-format
     */
    public static String convertToFlowchart(Model model, String orientation) {
        var result = "graph " + orientation + "\n";
        var iter = model.listStatements();

        ArrayList<String> uniqueList = new ArrayList<>();
        while (iter.hasNext()) {
            var current = iter.nextStatement();
            var subjectUri = current.getSubject().toString();
            var subject = getMMIdFromUri(subjectUri) + "[\"" + subjectUri + "\"]";

            var predicate = getMMIdFromUri(current.getPredicate().toString());

            var objectUri = current.getObject().toString().replace("\\\"", "");
            var object = getMMIdFromUri(objectUri) + "[\"" + objectUri + "\"]";

            var line = subject + "-->|" + predicate + "| " + object + "\n";
            result = result.concat(line);

            if (!uniqueList.contains(subjectUri)) {
                uniqueList.add(subjectUri);
            }
            if (!uniqueList.contains(objectUri)) {
                uniqueList.add(objectUri);
            }
        }
        for (var s : uniqueList) {
            result = result.concat("click " + getMMIdFromUri(s) + " call getID(\"" + s + "\")\n");
        }
        return result;
    }

    /**
     * Build a mermaid Compatible idFrom an uri
     *
     * @param uri The uri to be converted
     *
     * @return id
     */
    private static String getMMIdFromUri(String uri) {
        var split = uri.split("#");
        return split[split.length - 1].replaceAll(ONLY_ALPHANUMERICAL_REGEX, ".");
    }

    public String convertSchemaToMermaidUML() {
        if (model == null) {
            logger.error("Model is null");
            throw new IllegalArgumentException("Model is null");
        }
        prefixMapping = new PrefixMappingImpl();
        prefixMapping.setNsPrefixes(model.getNsPrefixMap());
        var result = new StringBuilder()
                  .append("---\n")
                  .append("config:\n")
                  .append("  theme: base\n")
                  .append("  themeVariables:\n")
                  .append("    fontFamily: \"arial\"\n")
                  .append("    textColor: \"#000\"\n")
                  .append("    primaryColor: \"#eee\"\n")
                  .append("    primaryBorderColor: \"#14162B\"\n")
                  .append("    lineColor: \"#14162B\"\n")
                  .append("---\n")
                  .append("classDiagram\n");
        var subjects = model.listSubjects();
        while (subjects.hasNext()) {
            var currentSubject = subjects.nextResource();
            result.append(classToUML(currentSubject));
            result.append(propertyToUML(currentSubject));
            result.append(enumToUML(currentSubject));
        }
        return result.toString();
    }

    //Classes
    private StringBuilder classToUML(Resource classResource) {
        var result = new StringBuilder();
        if (!classResource.hasProperty(RDF.type, RDFS.Class)) {
            return result;
        }
        var className = getResourceName(classResource, REMOVE_LANG_TAG);
        var classId = createIdFromUri(classResource.getURI());

        //add namespace
        var classDef = new StringBuilder()
                  .append("class `")
                  .append(classId)
                  .append("`[\"")
                  .append(className)
                  .append("\"]");
        if (classResource.hasProperty(CIMS.belongsToCategory)) {
            var packageResource = classResource.getProperty(CIMS.belongsToCategory).getResource();
            var packageName = getResourceName(packageResource, REMOVE_LANG_TAG);
            result.append("namespace ").append(packageName).append("{").append(classDef).append("}");
        } else {
            result.append(classDef);
        }

        result.append("\n");

        //add interactivity functionality
        result.append(functionCallbackDefinition(classId, classResource.getURI()));

        //add possible references to SuperClass
        result.append(getSuperClasses(classResource, classId));

        //Add possible comments to class
        result.append(getComments(classResource, classId));

        //add possible stereotypes
        result.append(getStereotypes(classResource, classId));
        return result;
    }

    private StringBuilder getSuperClasses(Resource classResource, String classId) {
        var result = new StringBuilder();
        if (!classResource.hasProperty(RDFS.subClassOf)) {
            return result;
        }
        var iterSuperClass = classResource.listProperties(RDFS.subClassOf);
        while (iterSuperClass.hasNext()) {
            var currentSuperClassStatement = iterSuperClass.nextStatement();
            var currentSuperClass = currentSuperClassStatement.getResource();
            var currentSuperClassId = createIdFromUri(currentSuperClass.getURI());
            result.append("`")
                  .append(currentSuperClassId)
                  .append("` <|-- `")
                  .append(classId)
                  .append("`\n");
        }
        return result;
    }

    private StringBuilder getComments(Resource classResource, String classId) {
        var result = new StringBuilder();
        if (!DISPLAY_COMMENT || !classResource.hasProperty(RDFS.comment)) {
            return result;
        }
        var comments = classResource.listProperties(RDFS.comment);
        while (comments.hasNext()) {
            var currentCommentStatement = comments.nextStatement();
            var currentComment = currentCommentStatement.getLiteral().getLexicalForm();
            currentComment = HtmlEscape.escapeHtml4(currentComment); //special chars can be shown as html encoded
            result.append("note for `")
                  .append(classId)
                  .append("` \"")
                  .append(currentComment)
                  .append("\"\n");
        }
        return result;
    }

    /**
     * Retrieves the stereotypes of a given class resource and formats them into a Mermaid UML syntax.
     *
     * @param classResource The class resource to retrieve the stereotypes from.
     * @param classId       The Mermaid ID of the class resource.
     *
     * @return A StringBuilder containing the stereotypes in Mermaid UML syntax.
     */
    private StringBuilder getStereotypes(Resource classResource, String classId) {
        var result = new StringBuilder();
        var className = getResourceName(classResource, REMOVE_LANG_TAG);

        if (!classResource.hasProperty(CIMS.stereotype)) {
            result.append("class `")
                  .append(classId)
                  .append("`[\"")
                  .append(className)
                  .append("\"]")
                  .append("{")
                  .append("<<abstract>>")
                  .append("\n}\n")
                  .append("\n");
            return result;
        }

        var stereotypeList = classResource.listProperties(CIMS.stereotype).toList();

        //remove concrete stereotype from list to include it in the UML string
        stereotypeList.removeIf(statement -> statement.getObject().equals(CIMStereotypes.concrete));

        if (stereotypeList.isEmpty()) {
            return result;
        }

        result.append("class `")
              .append(classId)
              .append("`[\"")
              .append(className)
              .append("\"]")
              .append("{")
              .append("<<");

        var insertArray = new ArrayList<String>();
        for (var stereotypeStatement : stereotypeList) {
            insertArray.add(getShortenedStereotypeName(stereotypeStatement.getObject()));
        }

        // add abstract stereotype if class is not concrete
        if (!classResource.hasProperty(CIMS.stereotype, CIMStereotypes.concrete)) {
            insertArray.add("abstract");
        }

        insertArray.sort(String::compareToIgnoreCase);

        return result.append(String.join(", ", insertArray))
                     .append(">>")
                     .append("}\n");
    }

    /**
     * If the stereotype is a known uri: retrieves a shortened name,
     * Otherwise returns the full string representation of the stereotype.
     *
     * @param stereotype The RDF node representing the stereotype.
     *
     * @return The name of the stereotype.
     */
    private String getShortenedStereotypeName(RDFNode stereotype) {
        if (stereotype.equals(CIMStereotypes.enumeration)) {
            return CIMStereotypes.enumeration.getLocalName();
        }
        return stereotype.toString();
    }

    //Properties
    private StringBuilder propertyToUML(Resource propertyResource) {
        //!!!mermaid cant add comments to properties!!!
        var result = new StringBuilder();
        if (!propertyResource.hasProperty(RDF.type, RDF.Property)) {
            return result;
        }
        var domain = propertyResource.getProperty(RDFS.domain).getResource(); //a class
        var domainId = createIdFromUri(domain.getURI());

        if (propertyResource.hasProperty(CIMS.stereotype, CIMStereotypes.attribute)) { //Property is a class attribute
            result.append(getAttributeString(propertyResource, domainId));
        } else { //Property is a relationship between classes
            result.append(getClassToClassRelationship(propertyResource, domainId));
        }
        return result;
    }

    private String getAttributeString(Resource attributeResource, String domainId) {
        var propertyName = getResourceName(attributeResource, REMOVE_LANG_TAG);
        Statement dataTypeStatement;
        if (attributeResource.hasProperty(RDFS.range)) {
            dataTypeStatement = attributeResource.getProperty(RDFS.range);
        } else {
            dataTypeStatement = attributeResource.getProperty(CIMS.datatype);
        }
        var dataTypeName = getResourceName(dataTypeStatement.getResource(), true);
        var defaultValue = "";
        if (attributeResource.hasProperty(CIMS.isDefault)) { //set default value if we have one
            defaultValue = attributeResource.getProperty(CIMS.isDefault).getLiteral().getLexicalForm();
            int endOfValue = defaultValue.indexOf("^");
            if (endOfValue != -1) {
                defaultValue = defaultValue.substring(0, endOfValue); //cut of possible tag
            }
            defaultValue = " = " + defaultValue;
        }
        return "`" + domainId + "` : " + dataTypeName + " " + propertyName + defaultValue + "\n";
    }

    private String getClassToClassRelationship(Resource relationResource, String thisId) { //domain
        var relationName = getResourceName(relationResource, REMOVE_LANG_TAG);
        var thatResource = relationResource.getProperty(RDFS.range).getResource();
        var thatID = createIdFromUri(thatResource.getURI());
        var associationThisToThat = relationResource.hasProperty(CIMS.associationUsed, "Yes") ? "-> " : "- ";
        var multiplicityThisToThat = getMultiplicity(relationResource);

        var associationThatToThis = " -";
        var multiplicityThatToThis = "";
        var inverseRelationName = "";
        if (relationResource.hasProperty(CIMS.inverseRoleName)) {
            var inverseRelationResource = relationResource.getProperty(CIMS.inverseRoleName).getResource();
            if (relationResource.toString().compareTo(inverseRelationResource.toString()) < 0) {
                return ""; //Early return to avoid double arrows
            }
            associationThatToThis = inverseRelationResource.hasProperty(CIMS.associationUsed, "Yes") ? " <-" : " -";
            multiplicityThatToThis = getMultiplicity(inverseRelationResource);
            //uri is in form of "http://something/something.something#relationName.inverseRelationName
            //we first split at # take the second string and then split at "." and take the second string
            inverseRelationName = getResourceName(inverseRelationResource, true) + "/";
        }

        var thisTo = multiplicityThatToThis + associationThatToThis;
        var toThat = associationThisToThat + multiplicityThisToThat;
        return "`" + thisId + "` " + thisTo + toThat + " `" + thatID + "` : " + inverseRelationName + relationName + "\n";
    }

    private String getMultiplicity(Resource property) {
        if (!property.hasProperty(CIMS.multiplicity)) {
            return "";
        }
        var multiplicity = property.getProperty(CIMS.multiplicity).getResource();
        var multiplicityString = multiplicity.toString().split("#M:", 2)[1]; //gets the name of the multiplicity
        return "\"" + multiplicityString + "\"";
    }

    //enums
    private StringBuilder enumToUML(Resource enumResource) {
        var result = new StringBuilder();
        if (!enumResource.hasProperty(CIMS.stereotype, "enum")) {
            return result;
        }
        var elementName = getResourceName(enumResource, REMOVE_LANG_TAG);
        var enumClass = enumResource.getProperty(RDF.type).getResource();
        var enumName = getResourceName(enumClass, false);
        result.append("`")
              .append(enumName)
              .append("` : ")
              .append(elementName)
              .append("\n");
        return result;
    }

    //utils
    private String getResourceName(Resource resource, boolean removeLangTag) {
        var resourceName = "";
        if (resource.hasProperty(RDFS.label)) {
            resourceName = resource.getProperty(RDFS.label).getLiteral().getLexicalForm();
        } else {//should never happen since everything should have a label
            resourceName = createIdFromUri(resource.toString());
        }
        if (removeLangTag) {
            resourceName = resourceName.replace("@en", "");
        }
        return resourceName;
    }

    //click classId call callBackFunctionName(uri)
    private String functionCallbackDefinition(String classId, String uri) {
        var res = substituteURI(uri);
        return "click " + classId + " call " + CALL_BACK_FUNCTION_NAME + "(" + res + ") \n";
    }

    //substitute the prefix and replace all special chars with '-', we do this because mermaid only allows very few special chars
    //we can then use this as an ID for the class and still set names including special chars
    private String createIdFromUri(String uri) {
        var res = substituteURI(uri);
        return res.replaceAll(ONLY_ALPHANUMERICAL_REGEX, "-");
    }

    private String substituteURI(String uri) {
        var res = "";
        try {
            var split = uri.split("#", 2);
            var substitutedPrefix = prefixMapping.getNsURIPrefix(split[0] + "#");
            assert substitutedPrefix != null;
            res = substitutedPrefix + ":" + split[1];
        } catch (Exception _) {
            res = uri;
        }
        return res;
    }
}
