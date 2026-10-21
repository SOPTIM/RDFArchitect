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
 * Reads an Enterprise Architect XMI export.
 *
 * <h2>What a file looks like</h2>
 *
 * Three parts, in this order, joined over {@code xmi:id}. Each reader in this package takes one of
 * them, and {@link org.rdfarchitect.xmi.read.XmiReader} hands every element to the one whose part
 * the walk is in. The diagrams are the exception: they are written in two pieces that sit in two
 * different parts, so one reader takes both. Shortened, with the reader that takes each line:
 *
 * <pre>{@code
 * <xmi:XMI>
 *
 *   <uml:Model>                                            XmiModelReader
 *     <packagedElement xmi:type="uml:Package" xmi:id="EAPK_1" name="Topology">
 *       <packagedElement xmi:type="uml:Class" xmi:id="EAID_1" name="TopologicalNode">
 *         <generalization general="EAID_9"/>                 the superclass
 *         <ownedAttribute xmi:id="EAID_2" name="pInjection">  a UML attribute
 *           <type xmi:idref="EAID_8"/>                        its type
 *           <lowerValue value="1"/><upperValue value="1"/>    its bounds
 *           <defaultValue value="0"/>
 *         </ownedAttribute>
 *         <ownedAttribute xmi:id="EAID_3" association="EAID_7"/>   an association end, skipped
 *       </packagedElement>
 *       <packagedElement xmi:type="uml:Enumeration" xmi:id="EAID_4" name="Currency">
 *         <ownedLiteral xmi:id="EAID_5" name="EUR"/>          an enum literal
 *       </packagedElement>
 *     </packagedElement>
 *
 *     <umldi:Diagram xmi:type="umldi:UMLClassDiagram">     XmiDiagramReader
 *       <ownedElement xmi:type="umldi:UMLClassifierShape" modelElement="EAID_1">
 *         <bounds x="711" y="1451"/>                        where a class is drawn
 *       </ownedElement>
 *     </umldi:Diagram>
 *   </uml:Model>
 *
 *   <xmi:Extension extender="Enterprise Architect">        XmiExtensionReader
 *     <elements>
 *       <element xmi:idref="EAPK_1" xmi:type="uml:Package">  the package again
 *         <properties documentation="The topology."/>
 *         <tags><tag name="nsuri" value="…#"/></tags>        its namespace, stated nowhere else
 *       </element>
 *       <element xmi:idref="EAID_9" xmi:type="uml:Class">    an ordinary class, no profile's
 *         <tags><tag name="nsuri:cim" value="…#"/></tags>     yet it carries the prefix table
 *       </element>
 *       <element xmi:idref="EAID_1" xmi:type="uml:Class">    the type the model got wrong
 *         <properties documentation="A node." isAbstract="false"/>
 *         <attribute xmi:idref="EAID_2">                     the UML attribute again
 *           <documentation value="Injected power."/>
 *           <properties changeability="frozen"/>             makes the default a fixed value
 *         </attribute>
 *       </element>
 *     </elements>
 *     <connectors>
 *       <connector xmi:idref="EAID_7">                       an association, both halves
 *         <source xmi:idref="EAID_1">
 *           <model name="TopologicalNode"/>
 *           <role name="Terminal"/>
 *           <type multiplicity="0..*" aggregation="none"/>
 *           <style value="…;Navigable=Navigable;…"/>
 *         </source>
 *         <target xmi:idref="EAID_6">…</target>
 *         <properties ea_type="Association"/>
 *       </connector>
 *     </connectors>
 *     <diagrams>                                           XmiDiagramReader, second piece
 *       <diagram xmi:id="…">                                the diagram's name and z-order
 *     </diagrams>
 *   </xmi:Extension>
 *
 *   <thecustomprofile:Entsoe base_Class="EAID_1"/>          XmiStereotypeReader
 *   <thecustomprofile:deprecated base_Property="EAID_2"/>
 *   <thecustomprofile:NC base_Association="EAID_7"/>          onto both halves of the pair
 *
 * </xmi:XMI>
 * }</pre>
 *
 * <h2>Why all three</h2>
 *
 * None of them is sufficient alone, and each is the only source of something:
 *
 * <ul>
 *   <li>whether a default value is <em>fixed</em> is only in the extension, as {@code
 *       changeability}, while the value itself is only in the model part. Reading one alone turns
 *       every fixed value into a default one, and a CGMES 2.4.15 profile's metadata is read from
 *       the fixed ones.
 *   <li>whether a {@code uml:Class} is really a class is only in the extension: a note, a boundary
 *       and a text box are all written as {@code uml:Class} in the model part.
 *   <li>an element's full set of stereotypes is only in the applications at the root. The
 *       extension's own {@code stereotype} attributes hold one value each and lose the co-occurring
 *       ones a release does emit.
 *   <li>the ends of an association are only in the connectors, the model part owning a varying
 *       number of them per export.
 *   <li>the namespaces are only in tagged values in the extension, and a diagram's name and z-order
 *       only in the extension's own diagram list, while everything drawn on that diagram is only in
 *       the UMLDI of the model part.
 * </ul>
 *
 * <p>The tagged values are worth a warning. A package states its own namespace on itself, but the
 * prefix table of a whole export is hung on one ordinary class belonging to no profile, so it is
 * collected wherever it turns up rather than from the thing it describes. See {@link
 * org.rdfarchitect.xmi.read.XmiPrefixTable}.
 *
 * <h2>How it is read</h2>
 *
 * In one streaming pass, because a full model reaches 100 MB and no tree representation survives
 * that. {@link org.rdfarchitect.xmi.read.XmiStreams} does the walking and says which part each
 * element belongs to; the readers share nothing but {@link
 * org.rdfarchitect.xmi.read.XmiReadContext}, which is the {@link
 * org.rdfarchitect.xmi.read.XmiIndex} they fill and the ids they look each other's work up by.
 *
 * <p>{@link org.rdfarchitect.xmi.read.XmiModelScanner} is a second, shallower pass that stops at
 * {@code </uml:Model>} and only reports which packages hold a profile, so that offering a choice of
 * profiles does not cost reading the whole file.
 *
 * <p>Names of the format are in {@link org.rdfarchitect.xmi.read.XmiNames} rather than spelled out.
 * Beware that "attribute" means three things here: an XML attribute, which {@code
 * XmiStreams.attribute} reads; an {@code <ownedAttribute>}, which is a UML attribute; and an {@code
 * <attribute>} of the extension, which is a UML attribute <em>or</em> an enum literal.
 */
package org.rdfarchitect.xmi.read;
