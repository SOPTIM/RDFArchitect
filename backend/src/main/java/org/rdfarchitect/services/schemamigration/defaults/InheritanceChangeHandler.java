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

package org.rdfarchitect.services.schemamigration.defaults;

import lombok.experimental.UtilityClass;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.rdfarchitect.models.changes.RenameCandidate;
import org.rdfarchitect.models.changes.semanticchanges.SemanticAssociationChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticAttributeChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticClassChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticFieldChangeType;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChange;
import org.rdfarchitect.models.changes.semanticchanges.SemanticResourceChangeType;
import org.rdfarchitect.models.cim.relations.model.CIMClassUtils;
import org.rdfarchitect.models.cim.relations.model.properties.CIMPropertyUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@UtilityClass
public class InheritanceChangeHandler {

    public void processInheritanceChanges(List<SemanticClassChange> classes, Model newModel, Model oldModel, List<RenameCandidate<SemanticClassChange>> classRenames) {
        var classChangeMap = classes.stream()
                                    .collect(Collectors.toMap(SemanticClassChange::getIri, c -> c));

        for (var cls : classes) {
            if (cls.getChanges().stream().anyMatch(change -> change.getSemanticFieldChangeType() == SemanticFieldChangeType.SUPERCLASS_CHANGE)) {
                addPropertyChangesFromInheritance(cls, classes, classChangeMap, newModel, oldModel, classRenames);
            }
        }

        classes.clear();
        classes.addAll(classChangeMap.values());
    }

    public void addPropertyChangesFromInheritance(SemanticClassChange classChange, List<SemanticClassChange> allClassChanges, Map<String, SemanticClassChange> classChangeMap,
                                                  Model newModel, Model oldModel, List<RenameCandidate<SemanticClassChange>> classRenames) {
        var oldUri = classChange.getOldIRI() != null ? classChange.getOldIRI() : classChange.getIri();
        var oldSuperClasses = CIMClassUtils.listSuperClasses(oldModel.getResource(oldUri));
        var newSuperClasses = CIMClassUtils.listSuperClasses(newModel.getResource(classChange.getIri()));

        var commonSuperClasses = new HashSet<>(oldSuperClasses);
        commonSuperClasses.retainAll(newSuperClasses);
        oldSuperClasses.removeAll(commonSuperClasses);
        newSuperClasses.removeAll(commonSuperClasses);

        removeRenamedClassesFromSuperClassChanges(oldSuperClasses, newSuperClasses, classRenames, oldModel, newModel);

        var propertiesToRemove = collectPropertiesToRemove(oldSuperClasses);
        var propertiesToAdd = collectPropertiesToAdd(newSuperClasses, allClassChanges);
        var oldIRI = classChange.getOldIRI() != null ? classChange.getOldIRI() : classChange.getIri();
        var oldDerivingClasses = CIMClassUtils.findDerivingClasses(oldModel.getResource(oldIRI));
        var newDerivingClasses = CIMClassUtils.findDerivingClasses(newModel.getResource(classChange.getIri()));

        if (CIMClassUtils.isInstantiableClass(newModel.getResource(classChange.getIri()))) {
            newDerivingClasses.add(newModel.getResource(classChange.getIri()));
            oldDerivingClasses.add(newModel.getResource(classChange.getIri()));
        }
        removeAddedClassesFromDerivingClasses(newDerivingClasses, allClassChanges);

        removeOldInheritedProperties(oldDerivingClasses, classChangeMap, propertiesToRemove);
        addNewInheritedProperties(newDerivingClasses, classChangeMap, propertiesToAdd);
    }

    private void removeRenamedClassesFromSuperClassChanges(Set<Resource> oldSuperClasses, Set<Resource> newSuperClasses, List<RenameCandidate<SemanticClassChange>> classRenames,
                                                           Model oldModel,
                                                           Model newModel) {
        for (var rename : classRenames) {
            var oldResource = oldModel.getResource(rename.getOldResource().getIri());
            var newResource = newModel.getResource(rename.getNewResource().getIri());
            if (oldSuperClasses.contains(oldResource) && newSuperClasses.contains(newResource)) {
                oldSuperClasses.remove(oldResource);
                newSuperClasses.remove(newResource);
            }
        }
    }

    private void removeAddedClassesFromDerivingClasses(Set<Resource> derivingClasses, List<SemanticClassChange> allClassChanges) {
        for (var classChange : allClassChanges) {
            if (classChange.getSemanticResourceChangeType() != SemanticResourceChangeType.ADD) {
                continue;
            }

            derivingClasses.removeIf(resource -> resource.getURI().equals(classChange.getIri()));
        }
    }

    private Set<Resource> collectPropertiesToRemove(Set<Resource> oldSuperClasses) {
        var propertiesToRemove = new HashSet<Resource>();
        for (var oldSuperClass : oldSuperClasses) {
            propertiesToRemove.addAll(CIMClassUtils.listDirectProperties(oldSuperClass));
        }
        return propertiesToRemove;
    }

    private Set<Resource> collectPropertiesToAdd(Set<Resource> newSuperClasses, List<SemanticClassChange> allClassChanges) {
        var propertiesToAdd = new HashSet<Resource>();
        for (var newSuperClass : newSuperClasses) {
            var superClassChangeObject = allClassChanges.stream()
                                                        .filter(c -> c.getIri().equals(newSuperClass.getURI()))
                                                        .findFirst()
                                                        .orElse(null);

            if (superClassChangeObject == null) {
                propertiesToAdd.addAll(CIMClassUtils.listDirectProperties(newSuperClass));
            } else {
                addPropertiesNotNewInSuperClass(propertiesToAdd, newSuperClass, superClassChangeObject);
            }
        }
        return propertiesToAdd;
    }

    private void addPropertiesNotNewInSuperClass(Set<Resource> propertiesToAdd, Resource newSuperClass, SemanticClassChange superClassChangeObject) {
        for (var property : CIMClassUtils.listDirectProperties(newSuperClass)) {
            boolean isNewInSuperClass = superClassChangeObject.getAttributes().stream()
                                                              .anyMatch(attr -> attr.getIri().equals(property.getURI()) &&
                                                                        attr.getSemanticResourceChangeType() == SemanticResourceChangeType.ADD) ||
                      superClassChangeObject.getAssociations().stream()
                                            .anyMatch(attr -> attr.getIri().equals(property.getURI()) &&
                                                      attr.getSemanticResourceChangeType() == SemanticResourceChangeType.ADD);
            if (!isNewInSuperClass) {
                propertiesToAdd.add(property);
            }
        }
    }

    private void removeOldInheritedProperties(Set<Resource> oldDerivingClasses, Map<String, SemanticClassChange> classChangesMap,
                                              Set<Resource> propertiesToRemove) {
        for (var derivingClass : oldDerivingClasses) {
            if (!CIMClassUtils.isInstantiableClass(derivingClass)) {
                continue;
            }

            var derivingClassChange = classChangesMap.computeIfAbsent(
                      derivingClass.getURI(),
                      _ -> new SemanticClassChange(derivingClass, SemanticResourceChangeType.CHANGE));

            for (var property : propertiesToRemove) {
                var propertyChange = new SemanticResourceChange(
                          property,
                          SemanticResourceChangeType.DELETED_FROM_INHERITANCE
                );
                propertyChange.setLabel(property.getURI().split("#")[1]);

                if (CIMPropertyUtils.isAttribute(property)) {
                    derivingClassChange.getAttributes().add(new SemanticAttributeChange(propertyChange));
                } else if (CIMPropertyUtils.isAssociation(property)) {
                    derivingClassChange.getAssociations().add(new SemanticAssociationChange(propertyChange));
                }
            }
        }
    }

    private void addNewInheritedProperties(Set<Resource> newDerivingClasses, Map<String, SemanticClassChange> classChangesMap,
                                           Set<Resource> propertiesToAdd) {
        for (var derivingClass : newDerivingClasses) {
            if (!CIMClassUtils.isInstantiableClass(derivingClass)) {
                continue;
            }

            var derivingClassChange = classChangesMap.computeIfAbsent(
                      derivingClass.getURI(),
                      _ -> new SemanticClassChange(derivingClass, SemanticResourceChangeType.CHANGE));

            for (var property : propertiesToAdd) {
                var propertyChange = new SemanticResourceChange(
                          property,
                          SemanticResourceChangeType.ADDED_FROM_INHERITANCE
                );
                propertyChange.setLabel(property.getURI().split("#")[1]);
                if (CIMPropertyUtils.isAttribute(property)) {
                    derivingClassChange.getAttributes().add(new SemanticAttributeChange(propertyChange));
                } else if (CIMPropertyUtils.isAssociation(property)) {
                    derivingClassChange.getAssociations().add(new SemanticAssociationChange(propertyChange));
                }
            }
        }
    }
}


