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

import { BackendConnection } from "$lib/api/backend.js";
import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
import { Class, DataType, DataTypeTypes, Package } from "$lib/models/dto";
import { classStore } from "$lib/stores/ClassStore.ts";
import { packageStore } from "$lib/stores/PackageStore.ts";

const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

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

    console.debug("PACKAGES:", packages);
    return packages;
}

export async function getDataTypes(datasetName, graphUri) {
    // fetch xsd datatypes
    const xsd = await bec.getXSDPrimitives();
    let xsdPrimitivesDto = await xsd.json();
    // fetch primitive datatypes
    const resPrimitivesClasses = await bec.getPrimitives(datasetName, graphUri);
    let primitivesDto = await resPrimitivesClasses.json();
    // fetch other datatypes (e.g. CIMDatatype)
    const resDataTypes = await bec.getDataTypes(datasetName, graphUri);
    let dataTypesDto = await resDataTypes.json();

    // combine all datatypes into one list
    let datatypes = [];
    for (const xsdDatatype of xsdPrimitivesDto) {
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
    console.debug("DATATYPES:", datatypes);
    return datatypes;
}

export async function getClasses(datasetName, graphUri) {
    await classStore.load(datasetName, graphUri, true);
    const classDTOs = await classStore.getClasses(datasetName, graphUri, true);
    let classes = classDTOs.map(cls => new Class(cls));
    console.debug("CLASSES:", classes);
    return classes;
}

export async function getStereotypes(datasetName, graphUri) {
    const res = await bec.getStereotypes(datasetName, graphUri);
    let stereotypesJSON = await res.json();

    console.debug("STEREOTYPES:", stereotypesJSON);
    return stereotypesJSON;
}
