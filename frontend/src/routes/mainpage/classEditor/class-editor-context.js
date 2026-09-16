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

import {
    findSuperClass,
    mapClassDtoToReactiveClass,
} from "$lib/models/reactive/mapper/map-dto-to-reactive-object.js";
import { datatypesStore } from "$lib/stores/datatypesStore.ts";
import { workspaceStore } from "$lib/stores/workspaceStore.ts";

import {
    getClasses,
    getDataTypes,
    getPackages,
} from "./fetch-class-editor-context.js";

export const openClassEditor = {
    register(source) {
        openEditorSource = source;
    },

    unregister(source) {
        if (openEditorSource === source) {
            openEditorSource = null;
        }
    },

    match(workspaceName, graphUri, classUuid) {
        const source = openEditorSource;
        if (!source || !classUuid || !source.reactiveClass) {
            return null;
        }
        if (
            source.classUuid !== classUuid ||
            source.workspaceName !== workspaceName
        ) {
            return null;
        }
        if (
            graphUri &&
            source.graphUri &&
            String(source.graphUri) !== String(graphUri)
        ) {
            return null;
        }
        return source;
    },
};

// A property editor for the class that is already open works on that class, so that two loaded
// copies cannot overwrite each other's unsaved changes.
let openEditorSource = null;

export async function loadClassEditorContextData(workspaceName, graphUri) {
    const [classes, packages, datatypes, stereotypes, namespaces] =
        await Promise.all([
            getClasses(workspaceName, graphUri),
            getPackages(workspaceName, graphUri),
            getDataTypes(workspaceName, graphUri),
            datatypesStore.getStereotypes(workspaceName, graphUri),
            workspaceStore.getNamespaces(workspaceName),
        ]);
    return { classes, packages, datatypes, stereotypes, namespaces };
}

export function createReactiveClass(classDto, data) {
    data.superClass = findSuperClass(data.classes, classDto);
    return mapClassDtoToReactiveClass(classDto, data, uuid =>
        data.targetClassInfos.find(cls => cls.uuid === uuid),
    );
}

export function createClassEditorContextValue(getSource, openClass = () => {}) {
    const data = () => getSource().data;
    return {
        get workspaceName() {
            return getSource().workspaceName;
        },
        get graphUri() {
            return getSource().graphUri;
        },
        get readOnly() {
            return getSource().readOnly;
        },
        get namespaces() {
            return data().namespaces;
        },
        get stereotypes() {
            return data().stereotypes;
        },
        get datatypes() {
            return data().datatypes;
        },
        get classes() {
            return data().classes;
        },
        get packages() {
            return data().packages;
        },
        get reactiveClass() {
            return getSource().reactiveClass;
        },
        get targetClassInfos() {
            return data().targetClassInfos;
        },
        get getClassByUuid() {
            return function (uuid) {
                const cls = data().classes.find(cls => cls.uuid === uuid);
                if (cls) {
                    return cls;
                }
                return data().superClass?.uuid === uuid
                    ? data().superClass
                    : undefined;
            };
        },
        get getTargetClassInfoByUuid() {
            return function (uuid) {
                return data().targetClassInfos.find(cls => cls.uuid === uuid);
            };
        },
        get getSubstitutedNamespace() {
            return function (namespace) {
                const namespaceObj = data().namespaces.find(
                    p => p.prefix === namespace,
                );
                let returnValue = namespaceObj
                    ? namespaceObj.substitutedPrefix
                    : namespace;
                if (returnValue && returnValue.endsWith(":")) {
                    returnValue = returnValue.slice(0, -1);
                }
                return returnValue;
            };
        },
        get getDatatypeByUri() {
            return function (uri) {
                return data().datatypes.find(
                    dt => dt.prefix + dt.label === uri,
                );
            };
        },
        get getPackageByUuid() {
            return function (uuid) {
                return data().packages.find(pkg => pkg.uuid === uuid);
            };
        },
        addTargetClassInfo(classInfo) {
            data().targetClassInfos = [...data().targetClassInfos, classInfo];
        },
        openClass,
    };
}
