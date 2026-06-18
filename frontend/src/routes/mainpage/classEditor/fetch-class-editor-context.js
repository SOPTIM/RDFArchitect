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

import { Class, DataType, DataTypeTypes, Package } from "$lib/models/dto";
import { classStore } from "$lib/stores/ClassStore.ts";
import { datatypesStore } from "$lib/stores/DatatypesStore.ts";
import { packageStore } from "$lib/stores/PackageStore.ts";
import {
    getXSDPrimitives,
    loadXsdPrimitives,
} from "$lib/stores/XSDDatatypesStore.ts";

export async function getPackages(datasetName, graphUri) {
    // fetch packages
    await packageStore.load(datasetName, graphUri);
    const packageData = packageStore.getPackages(datasetName, graphUri);

    // Combine internal and external packages
    let packages = [];
    for (const pkg of packageData.internal) {
        packages.push(new Package(pkg));
    }
    for (const pkg of packageData.external) {
        packages.push(new Package({ ...pkg, external: true }));
    }

    packages.sort((a, b) =>
        a.label.localeCompare(b.label, undefined, {
            sensitivity: "base",
        }),
    );

    console.log("PACKAGES:", packages);
    return packages;
}

export async function getDataTypes(datasetName, graphUri) {
    await loadXsdPrimitives();
    await datatypesStore.loadForGraph(datasetName, graphUri);
    const xsd = await getXSDPrimitives();
    const primitivesDto = await datatypesStore.getPrimitives(
        datasetName,
        graphUri,
    );
    const dataTypesDto = await datatypesStore.getDatatypes(
        datasetName,
        graphUri,
    );

    // combine all datatypes into one list
    let datatypes = [];
    for (const xsdDatatype of xsd) {
        datatypes.push(
            new DataType({
                prefix: xsdDatatype.prefix,
                label: xsdDatatype.suffix,
                type: DataTypeTypes.PRIMITIVE,
            }),
        );
    }
    for (const primitiveDatatype of primitivesDto) {
        datatypes.push(
            new DataType({
                prefix: primitiveDatatype.prefix,
                label: primitiveDatatype.label,
                type: DataTypeTypes.PRIMITIVE,
            }),
        );
    }
    for (const datatype of dataTypesDto) {
        datatypes.push(
            new DataType({
                prefix: datatype.prefix,
                label: datatype.label,
                type: DataTypeTypes.RANGE,
            }),
        );
    }

    datatypes.sort((a, b) =>
        a.label.localeCompare(b.label, undefined, {
            sensitivity: "base",
        }),
    );
    console.log("DATATYPES:", datatypes);
    return datatypes;
}

export async function getClasses(datasetName, graphUri) {
    await classStore.load(datasetName, graphUri, true);
    const classDTOs = await classStore.getClasses(datasetName, graphUri, true);
    let classes = classDTOs.map(cls => new Class(cls));
    console.log("CLASSES:", classes);
    return classes;
}
