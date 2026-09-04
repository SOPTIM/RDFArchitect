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

/**
 * The vocabularies a constraints file is written *in*, as opposed to the one it is written *about*.
 *
 * `/shacl/terms` answers from the workspace's schema index, which holds CIM classes and properties.
 * SHACL's own vocabulary is in no profile, so nothing there knows `sh:minCount` exists — which left
 * the editor completing every term in a file except the ones that make it a constraints file.
 *
 * Kept in the browser rather than served, because unlike the schema this does not vary by
 * workspace, by profile or by version: it is the same in every RDFArchitect installation and in
 * every other SHACL tool. A round trip would buy nothing and would make completion depend on the
 * backend being reachable.
 *
 * Not exhaustive by design. What is here is what someone authoring constraints types; the
 * validation-report vocabulary (`sh:conforms`, `sh:resultPath` and friends) is left out because it
 * is read from a report, never written into a shapes file.
 */

export const SHACL_NS = "http://www.w3.org/ns/shacl#";
const RDF_NS = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
const RDFS_NS = "http://www.w3.org/2000/01/rdf-schema#";
const OWL_NS = "http://www.w3.org/2002/07/owl#";
const XSD_NS = "http://www.w3.org/2001/XMLSchema#";

/** Namespaces this module answers for. Anything else is the schema index's business. */
export const STANDARD_NAMESPACES = [SHACL_NS, RDF_NS, RDFS_NS, OWL_NS, XSD_NS];

/** `[localName, comment]` pairs, grouped so the table stays readable. */
const VOCABULARY = [
    [
        SHACL_NS,
        "PROPERTY",
        [
            // What a shape applies to
            [
                "targetClass",
                "Applies this shape to every instance of the class.",
            ],
            ["targetNode", "Applies this shape to one named node."],
            [
                "targetObjectsOf",
                "Applies this shape to everything used as the object of the property.",
            ],
            [
                "targetSubjectsOf",
                "Applies this shape to everything used as the subject of the property.",
            ],

            // Structure
            ["property", "A property shape this node shape includes."],
            ["path", "The property the surrounding property shape constrains."],
            ["node", "The node shape every value must also satisfy."],

            // Cardinality and type
            [
                "minCount",
                "The fewest values allowed. Absent means none are required.",
            ],
            ["maxCount", "The most values allowed. Absent means any number."],
            ["datatype", "The datatype every literal value must have."],
            ["class", "The class every value must be an instance of."],
            [
                "nodeKind",
                "Whether values are IRIs, blank nodes or literals — one of the sh: node kinds.",
            ],

            // Value range
            ["minInclusive", "Values must be at least this."],
            ["maxInclusive", "Values must be at most this."],
            ["minExclusive", "Values must be greater than this."],
            ["maxExclusive", "Values must be less than this."],

            // Strings
            ["minLength", "The shortest string allowed."],
            ["maxLength", "The longest string allowed."],
            ["pattern", "A regular expression every value must match."],
            ["flags", "Regular-expression flags for the sh:pattern beside it."],
            ["languageIn", "The language tags allowed on values."],
            [
                "uniqueLang",
                "Forbids two values carrying the same language tag.",
            ],

            // Values
            [
                "in",
                "The list of values allowed — anything else is a violation.",
            ],
            ["hasValue", "A value the property must have among its values."],

            // Between properties
            ["equals", "The two properties must have exactly the same values."],
            ["disjoint", "The two properties must share no value."],
            [
                "lessThan",
                "Every value must be less than every value of the named property.",
            ],
            [
                "lessThanOrEquals",
                "Every value must be at most every value of the named property.",
            ],

            // Logic
            ["not", "The shape values must *not* satisfy."],
            ["and", "A list of shapes values must all satisfy."],
            ["or", "A list of shapes values must satisfy at least one of."],
            ["xone", "A list of shapes values must satisfy exactly one of."],

            // Closed shapes
            [
                "closed",
                "When true, forbids any property the shape does not mention on the target.",
            ],
            [
                "ignoredProperties",
                "Properties a closed shape tolerates anyway, as a list.",
            ],

            // Qualified shapes
            ["qualifiedValueShape", "A shape some of the values must satisfy."],
            [
                "qualifiedMinCount",
                "How many values must satisfy sh:qualifiedValueShape.",
            ],
            [
                "qualifiedMaxCount",
                "How many values may satisfy sh:qualifiedValueShape.",
            ],
            [
                "qualifiedValueShapesDisjoint",
                "Requires the qualified value shapes to have no value in common.",
            ],

            // Reporting
            ["name", "A display name for the shape."],
            ["description", "What the shape is for, in prose."],
            ["message", "What to say when the constraint is violated."],
            [
                "severity",
                "How bad a violation is: sh:Violation, sh:Warning or sh:Info.",
            ],
            [
                "deactivated",
                "When true, the shape is ignored during validation.",
            ],
            ["group", "The property group this shape is displayed under."],
            ["order", "Where the shape sorts within its group."],

            // Embedded SPARQL
            ["sparql", "A SPARQL-based constraint."],
            [
                "select",
                "The SELECT query a SPARQL constraint is violated by the results of.",
            ],
            ["ask", "The ASK query a SPARQL constraint must answer true for."],
            [
                "prefixes",
                "The prefix declarations the embedded SPARQL is read with.",
            ],
            ["declare", "One prefix declaration."],
            ["prefix", "The prefix being declared."],
            ["namespace", "The namespace the prefix stands for."],

            // Path expressions
            ["inversePath", "Follows the property backwards."],
            ["alternativePath", "Follows any one of a list of paths."],
            [
                "zeroOrMorePath",
                "Follows the path any number of times, including none.",
            ],
            ["oneOrMorePath", "Follows the path one or more times."],
            ["zeroOrOnePath", "Follows the path at most once."],
        ],
    ],
    [
        SHACL_NS,
        "CLASS",
        [
            [
                "NodeShape",
                "A shape that applies to nodes — normally to a class's instances.",
            ],
            [
                "PropertyShape",
                "A shape that applies to the values reached by one path.",
            ],
            [
                "Shape",
                "The common supertype of node shapes and property shapes.",
            ],
            ["SPARQLConstraint", "A constraint expressed as a SPARQL query."],
            ["PropertyGroup", "A heading property shapes are displayed under."],
            ["PrefixDeclaration", "One prefix binding for embedded SPARQL."],
            ["NodeKind", "The class of the six node kinds."],
            ["Severity", "The class of sh:Violation, sh:Warning and sh:Info."],
        ],
    ],
    [
        SHACL_NS,
        "ENUM_MEMBER",
        [
            ["Violation", "Severity: the data is wrong."],
            ["Warning", "Severity: worth looking at, not a failure."],
            ["Info", "Severity: a remark."],
            ["IRI", "Node kind: values must be IRIs."],
            ["BlankNode", "Node kind: values must be blank nodes."],
            ["Literal", "Node kind: values must be literals."],
            [
                "BlankNodeOrIRI",
                "Node kind: values must be blank nodes or IRIs.",
            ],
            [
                "BlankNodeOrLiteral",
                "Node kind: values must be blank nodes or literals.",
            ],
            ["IRIOrLiteral", "Node kind: values must be IRIs or literals."],
        ],
    ],
    [
        RDF_NS,
        "PROPERTY",
        [
            ["type", "What the subject is an instance of."],
            ["first", "The head of an RDF collection."],
            ["rest", "The tail of an RDF collection."],
        ],
    ],
    [
        RDF_NS,
        "CLASS",
        [
            ["Property", "The class of RDF properties."],
            ["List", "The class of RDF collections."],
            [
                "langString",
                "The datatype of a literal carrying a language tag.",
            ],
            ["HTML", "The datatype of an HTML fragment literal."],
            ["JSON", "The datatype of a JSON literal."],
        ],
    ],
    [RDF_NS, "ENUM_MEMBER", [["nil", "The empty RDF collection."]]],
    [
        RDFS_NS,
        "PROPERTY",
        [
            ["label", "A human-readable name."],
            ["comment", "A human-readable description."],
            ["subClassOf", "The class this one specialises."],
            ["subPropertyOf", "The property this one specialises."],
            ["domain", "The class a subject of this property belongs to."],
            [
                "range",
                "The class or datatype the values of this property belong to.",
            ],
            ["seeAlso", "Something else worth looking at."],
            ["isDefinedBy", "Where the term is defined."],
        ],
    ],
    [
        RDFS_NS,
        "CLASS",
        [
            ["Class", "The class of classes."],
            ["Resource", "The class everything belongs to."],
            ["Literal", "The class of literal values."],
            ["Datatype", "The class of datatypes."],
        ],
    ],
    [
        OWL_NS,
        "PROPERTY",
        [
            ["versionIRI", "The IRI identifying this version of the ontology."],
            ["versionInfo", "The version, as text."],
            ["imports", "An ontology this one includes."],
        ],
    ],
    [
        OWL_NS,
        "CLASS",
        [
            [
                "Ontology",
                "The class of ontologies — the header of a profile or constraints file.",
            ],
            ["Class", "The OWL class of classes."],
            ["DatatypeProperty", "A property whose values are literals."],
            ["ObjectProperty", "A property whose values are resources."],
        ],
    ],
    [
        XSD_NS,
        "CLASS",
        [
            ["string", "Character string."],
            ["boolean", "true or false."],
            ["decimal", "Arbitrary-precision decimal number."],
            ["integer", "Whole number of any size."],
            ["int", "32-bit signed whole number."],
            ["long", "64-bit signed whole number."],
            ["short", "16-bit signed whole number."],
            ["byte", "8-bit signed whole number."],
            ["float", "Single-precision floating point number."],
            ["double", "Double-precision floating point number."],
            ["dateTime", "Date and time, e.g. 2026-08-31T12:00:00Z."],
            ["date", "Calendar date, e.g. 2026-08-31."],
            ["time", "Time of day."],
            ["duration", "A length of time."],
            ["gYear", "A Gregorian year, e.g. 2026."],
            ["gMonth", "A Gregorian month, e.g. --08."],
            ["gDay", "A Gregorian day of the month, e.g. ---31."],
            ["gYearMonth", "A Gregorian year and month, e.g. 2026-08."],
            ["gMonthDay", "A Gregorian month and day, e.g. --08-31."],
            ["anyURI", "A URI."],
            ["base64Binary", "Base64-encoded binary data."],
            ["hexBinary", "Hex-encoded binary data."],
            ["normalizedString", "String with no tabs or line breaks."],
            [
                "token",
                "Normalized string with no leading, trailing or repeated spaces.",
            ],
            ["language", "A language tag, e.g. en-GB."],
            ["nonNegativeInteger", "Whole number of zero or more."],
            ["positiveInteger", "Whole number of one or more."],
            ["nonPositiveInteger", "Whole number of zero or less."],
            ["negativeInteger", "Whole number of minus one or less."],
            ["unsignedInt", "32-bit unsigned whole number."],
            ["unsignedLong", "64-bit unsigned whole number."],
        ],
    ],
];

/**
 * Every standard term, in the shape the schema endpoint returns its own.
 *
 * Same shape on purpose: completion, hover and the writer all take terms as
 * `{ kind, iri, namespace, localName, label, comment }`, so nothing downstream needs to know that
 * some terms came from a table and some from the schema index.
 */
export const STANDARD_TERMS = VOCABULARY.flatMap(([namespace, kind, entries]) =>
    entries.map(([localName, comment]) => ({
        kind,
        iri: namespace + localName,
        namespace,
        localName,
        label: null,
        comment,
        domains: [],
        ranges: [],
        profiles: [],
    })),
);

const BY_IRI = new Map(STANDARD_TERMS.map(term => [term.iri, term]));

/**
 * What this module knows about a term, or `null` when the term is not one of its own.
 *
 * The namespaces here are disjoint from any CIM profile, so a hit is definitive and a miss is a
 * question for the schema index rather than a reason to give up.
 */
export function standardDetailOf(iri) {
    return BY_IRI.get(iri) ?? null;
}
