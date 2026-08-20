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

import { writable, get } from "svelte/store";

import { type GraphKey, loadSlot, makeGraphKey } from "./storeHelpers";
import { describeError } from "./storeLogging";
import { type AsyncSlot, createEmptySlot, type Result } from "./storeTypes";
import {
    // class list & details
    getClassList,
    getClassInformation,
    // class-level mutations
    addClass,
    replaceClass,
    extendClass,
    pasteClasses,
    // attribute mutations
    createAttribute,
    replaceAttribute,
    // association mutations
    createAssociation,
    replaceAssociation,
    // enum entry mutations
    createEnumEntry,
    replaceEnumEntry,
    // types
    type AddNewClassRequest,
    type AssociationPairDto,
    type AttributeDto,
    type ClassDto,
    type ClassUmlAdaptedDto,
    type EnumEntryDto,
    type AssociationUuids,
    type PasteClassesRequestDto,
    type CopyClassResponseDto,
} from "../api/generated";
import { toastStore } from "../eventhandling/toastStore.svelte.js";

// Two cache slots per graph: the list endpoint may be called with or without
// external classes; keep both so toggling the flag does not invalidate the
// other one.
type Variant = "all" | "internalOnly";

type VariantState = AsyncSlot<ClassUmlAdaptedDto[]>;

type ClassDtoWithMetadata = ClassUmlAdaptedDto & { _detailsLoaded?: true };

type GraphClassState = {
    all: VariantState;
    internalOnly: VariantState;
};

type ClassesState = {
    byGraph: Map<GraphKey, GraphClassState>;
    pendingDetails: Map<string, Promise<ClassUmlAdaptedDto | null>>;
};

const LOG_PREFIX = "[classStore]";

export const classStore = createClassStore();

function createEmptyGraphState(): GraphClassState {
    return {
        all: createEmptySlot(),
        internalOnly: createEmptySlot(),
    };
}

// ----- Helpers -----

function getId(c: ClassUmlAdaptedDto): string | undefined {
    return c.uuid;
}

// The list endpoint returns classes without details: `attributes`,
// `enumEntries` and `associationPairs` are omitted. The detail endpoint fills
// them and sets the detailsLoaded flag.
function hasDetails(c: ClassUmlAdaptedDto | null | undefined): boolean {
    return !!(c as ClassDtoWithMetadata)?._detailsLoaded;
}

function findInVariant(
    list: ClassUmlAdaptedDto[] | null,
    uuid: string,
): ClassUmlAdaptedDto | undefined {
    if (!list) return undefined;
    return list.find(c => getId(c) === uuid);
}

function upsertClass(
    list: ClassUmlAdaptedDto[],
    cls: ClassUmlAdaptedDto,
): ClassUmlAdaptedDto[] {
    const id = getId(cls);
    if (!id) return list.includes(cls) ? list : [...list, cls];
    const idx = list.findIndex(c => getId(c) === id);
    if (idx >= 0) {
        const next = [...list];
        next[idx] = cls;
        return next;
    }
    return [...list, cls];
}

// Generic UUID-based upsert for sub-resources (attribute / enum entry).
function upsertByUuid<T extends { uuid?: string }>(list: T[], item: T): T[] {
    if (!item.uuid) return [...list, item];
    const idx = list.findIndex(x => x.uuid === item.uuid);
    if (idx >= 0) {
        const next = [...list];
        next[idx] = item;
        return next;
    }
    return [...list, item];
}

// Association pairs are identified by their `from.uuid` (mirrors the backend
// path `…/associations/{associationUUID}` which expects the from-end's UUID).
function upsertAssociationPair(
    list: AssociationPairDto[],
    pair: AssociationPairDto,
): AssociationPairDto[] {
    const id = pair.from?.uuid;
    if (!id) return [...list, pair];
    const idx = list.findIndex(p => p.from?.uuid === id);
    if (idx >= 0) {
        const next = [...list];
        next[idx] = pair;
        return next;
    }
    return [...list, pair];
}

function createClassStore() {
    const store = writable<ClassesState>({
        byGraph: new Map(),
        pendingDetails: new Map(),
    });
    const { subscribe, update } = store;

    function getGraphState(
        state: ClassesState,
        key: GraphKey,
    ): GraphClassState {
        return state.byGraph.get(key) ?? createEmptyGraphState();
    }

    function setGraphState(
        state: ClassesState,
        key: GraphKey,
        next: GraphClassState,
    ): ClassesState {
        const byGraph = new Map(state.byGraph);
        byGraph.set(key, next);
        return { ...state, byGraph };
    }

    function setVariant(
        state: ClassesState,
        key: GraphKey,
        variant: Variant,
        next: VariantState,
    ): ClassesState {
        const current = getGraphState(state, key);
        return setGraphState(state, key, { ...current, [variant]: next });
    }

    // Apply a transformation to a single class (if it exists) in both variants.
    function mutateClassInPlace(
        key: GraphKey,
        classUUID: string,
        transform: (c: ClassUmlAdaptedDto) => ClassUmlAdaptedDto,
    ) {
        update(s => {
            const current = s.byGraph.get(key);
            if (!current) return s;

            const apply = (variant: VariantState): VariantState => {
                if (!variant.data) return variant;
                const idx = variant.data.findIndex(c => getId(c) === classUUID);
                if (idx < 0) return variant;
                const nextList = [...variant.data];
                nextList[idx] = transform(nextList[idx]);
                return {
                    ...variant,
                    data: nextList,
                    fetchedAt: Date.now(),
                    error: null,
                };
            };

            return setGraphState(s, key, {
                all: apply(current.all),
                internalOnly: apply(current.internalOnly),
            });
        });
    }

    // ----- Getters -----

    async function getClasses(
        datasetName: string,
        graphURI: string,
        includeExternal = false,
        force = false,
    ): Promise<ClassUmlAdaptedDto[] | null> {
        if (!datasetName || !graphURI) return null;
        const key = makeGraphKey(datasetName, graphURI);
        const variant: Variant = includeExternal ? "all" : "internalOnly";

        return loadSlot(
            store,
            s => getGraphState(s, key)[variant],
            (s, patch) =>
                setVariant(s, key, variant, {
                    ...getGraphState(s, key)[variant],
                    ...patch,
                }),
            () =>
                getClassList({
                    path: { datasetName, graphURI },
                    query: { includeExternalClasses: includeExternal },
                }),
            LOG_PREFIX,
            `classes for dataset="${datasetName}", graph="${graphURI}", variant="${variant}"`,
            force,
        );
    }

    async function getClassInfo(
        datasetName: string,
        graphURI: string,
        classUUID: string,
        force = false,
    ): Promise<ClassUmlAdaptedDto | null> {
        if (!datasetName || !graphURI || !classUUID) return null;

        const key = makeGraphKey(datasetName, graphURI);
        const pendingKey = `${key}::${classUUID}`;

        if (!force) {
            const current = getGraphState(get(store), key);
            const existing =
                findInVariant(current.all.data, classUUID) ??
                findInVariant(current.internalOnly.data, classUUID);
            if (hasDetails(existing)) return existing ?? null;

            const running = get(store).pendingDetails.get(pendingKey);
            if (running) return running;
        }

        console.log(
            `${LOG_PREFIX} Loading class details for classUUID="${classUUID}" in dataset="${datasetName}", graph="${graphURI}"`,
        );

        const promise = (async (): Promise<ClassUmlAdaptedDto | null> => {
            const { data, error } = await getClassInformation({
                path: { datasetName, graphURI, classUUID },
                query: { includeSuperClasses: true },
            });

            update(s => {
                const pendingDetails = new Map(s.pendingDetails);
                pendingDetails.delete(pendingKey);
                return { ...s, pendingDetails };
            });

            if (error || !data) {
                if (error) {
                    console.error(
                        `${LOG_PREFIX} Failed to load class details for classUUID="${classUUID}"`,
                        await describeError(error),
                    );
                } else {
                    console.error(
                        `${LOG_PREFIX} Class details response was empty for classUUID="${classUUID}"`,
                    );
                }
                return null;
            }

            const enriched: ClassDtoWithMetadata = {
                ...data,
                _detailsLoaded: true,
            };
            update(s => {
                const current = getGraphState(s, key);
                const mergeInto = (variant: VariantState): VariantState => {
                    if (!variant.data) return variant;
                    return {
                        ...variant,
                        data: upsertClass(variant.data, enriched),
                        fetchedAt: Date.now(),
                        error: null,
                    };
                };
                return setGraphState(s, key, {
                    all: mergeInto(current.all),
                    internalOnly: mergeInto(current.internalOnly),
                });
            });

            console.log(
                `${LOG_PREFIX} Loaded class details for classUUID="${classUUID}"`,
            );

            return enriched;
        })();

        update(s => {
            const pendingDetails = new Map(s.pendingDetails);
            pendingDetails.set(pendingKey, promise);
            return { ...s, pendingDetails };
        });

        return promise;
    }

    // =========================================================================
    // CLASS-LEVEL OPERATIONS
    // =========================================================================

    async function addNewClass(
        datasetName: string,
        graphURI: string,
        request: AddNewClassRequest,
    ): Promise<Result<string>> {
        console.log(
            `${LOG_PREFIX} Creating class in dataset="${datasetName}", graph="${graphURI}"`,
        );

        const { data, error } = await addClass({
            path: { datasetName, graphURI },
            body: request,
        });
        if (error) {
            console.error(
                `${LOG_PREFIX} Could not create class`,
                await describeError(error),
            );
            toastStore.error("Create failed", "Class could not be created.");
            return { error };
        }

        invalidateGraph(datasetName, graphURI);
        console.log(`${LOG_PREFIX} Created class with uuid="${data ?? ""}"`);
        toastStore.success("Class created", "Class successfully created.");
        return { error: null, data: data ?? undefined };
    }

    async function pasteCopiedClasses(
        datasetName: string,
        graphURI: string,
        request: PasteClassesRequestDto,
    ): Promise<Result<CopyClassResponseDto[]>> {
        const sources = request.sources ?? [];
        const sourceCount = sources.length;

        if (sourceCount === 0) {
            console.warn(
                `${LOG_PREFIX} pasteCopiedClasses called with no sources`,
            );
            return { error: null };
        } else if (sourceCount === 1) {
            const { classUUID } = sources[0];
            console.log(
                `${LOG_PREFIX} Pasting class classUUID="${classUUID}" into dataset="${datasetName}", graph="${graphURI}"`,
            );
        } else {
            console.log(
                `${LOG_PREFIX} Pasting ${sourceCount} classes into dataset="${datasetName}", graph="${graphURI}"`,
            );
        }

        const { data, error } = await pasteClasses({
            path: {
                targetDatasetName: datasetName,
                targetGraphURI: graphURI,
            },
            body: request,
        });

        if (error || !data) {
            console.error(
                sourceCount === 1
                    ? `${LOG_PREFIX} Could not paste class classUUID="${sources[0].classUUID}"`
                    : `${LOG_PREFIX} Could not paste ${sourceCount} classes`,
                await describeError(error),
            );
            toastStore.error("Paste failed", `Could not paste classes.`);

            return { error };
        }

        invalidateGraph(datasetName, graphURI);
        if (sourceCount === 1) {
            console.log(
                `${LOG_PREFIX} Successfully pasted class ${data[0].name}"`,
            );
            toastStore.success("Class pasted", `"${data[0].name}" was pasted.`);
        } else {
            console.log(
                `${LOG_PREFIX} Successfully pasted ${data.length ?? sourceCount} classes`,
            );
            toastStore.success(
                "Classes pasted",
                `${data.length} classes were pasted.`,
            );
        }

        return {
            error: null,
            data: data,
        };
    }

    async function replaceExistingClass(
        datasetName: string,
        graphURI: string,
        classUUID: string,
        cls: ClassUmlAdaptedDto,
    ): Promise<Result> {
        console.log(`${LOG_PREFIX} Replacing class classUUID="${classUUID}"`);

        const { error } = await replaceClass({
            path: { datasetName, graphURI, classUUID },
            body: cls,
        });
        if (error) {
            console.error(
                `${LOG_PREFIX} Could not replace class classUUID="${classUUID}"`,
                await describeError(error),
            );
            toastStore.error("Save failed", "Class could not be saved.");
            return { error };
        }

        const key = makeGraphKey(datasetName, graphURI);
        mutateClassInPlace(key, classUUID, prev => ({
            ...prev,
            ...cls,
            uuid: classUUID,
        }));
        console.log(`${LOG_PREFIX} Replaced class classUUID="${classUUID}"`);
        toastStore.success("Class saved", "Changes were applied.");
        return { error: null };
    }

    async function extendExistingClass(
        datasetName: string,
        graphURI: string,
        classUUID: string,
        attribute: AttributeDto,
    ): Promise<Result<ClassDto>> {
        console.log(`${LOG_PREFIX} Extending class classUUID="${classUUID}"`);

        const { data, error } = await extendClass({
            path: { datasetName, graphURI, classUUID },
            body: attribute,
        });
        if (error) {
            console.error(
                `${LOG_PREFIX} Could not extend class classUUID="${classUUID}"`,
                await describeError(error),
            );
            toastStore.error(
                "Extension failed",
                "Class could not be extended.",
            );
            return { error };
        }

        invalidateGraph(datasetName, graphURI);
        console.log(`${LOG_PREFIX} Extended class classUUID="${classUUID}"`);
        toastStore.success("Class extended", "Class was extended.");
        return { error: null, data: data ?? undefined };
    }

    // =========================================================================
    // ATTRIBUTE OPERATIONS
    // =========================================================================

    async function addAttribute(
        datasetName: string,
        graphURI: string,
        classUUID: string,
        attribute: AttributeDto,
    ): Promise<Result<string>> {
        console.log(
            `${LOG_PREFIX} Adding attribute to class classUUID="${classUUID}"`,
        );

        const { data, error } = await createAttribute({
            path: { datasetName, graphURI, classUUID },
            body: attribute,
        });
        if (error) {
            console.error(
                `${LOG_PREFIX} Could not add attribute to class classUUID="${classUUID}"`,
                await describeError(error),
            );
            toastStore.error(
                "Add attribute failed",
                "Attribute could not be added.",
            );
            return { error };
        }

        const newUUID = data ?? attribute.uuid;
        const stored: AttributeDto = { ...attribute, uuid: newUUID };

        const key = makeGraphKey(datasetName, graphURI);
        mutateClassInPlace(key, classUUID, prev => ({
            ...prev,
            attributes: upsertByUuid(prev.attributes ?? [], stored),
        }));

        console.log(
            `${LOG_PREFIX} Added attribute uuid="${newUUID ?? ""}" to class classUUID="${classUUID}"`,
        );
        toastStore.success(
            "Attribute added",
            "Attribute was added successfully.",
        );
        return { error: null, data: newUUID };
    }

    async function replaceExistingAttribute(
        datasetName: string,
        graphURI: string,
        classUUID: string,
        attribute: AttributeDto,
    ): Promise<Result<string>> {
        if (!attribute.uuid) {
            const err = new Error("attribute.uuid is required");
            console.error(
                `${LOG_PREFIX} replaceAttribute validation failed`,
                err,
            );
            return { error: err };
        }

        console.log(
            `${LOG_PREFIX} Replacing attribute uuid="${attribute.uuid}" in class classUUID="${classUUID}"`,
        );

        const { data, error } = await replaceAttribute({
            path: {
                datasetName,
                graphURI,
                classUUID,
                attributeUUID: attribute.uuid,
            },
            body: attribute,
        });
        if (error) {
            console.error(
                `${LOG_PREFIX} Could not replace attribute uuid="${attribute.uuid}"`,
                await describeError(error),
            );
            toastStore.error("Save failed", "Attribute could not be saved.");
            return { error };
        }

        const key = makeGraphKey(datasetName, graphURI);
        mutateClassInPlace(key, classUUID, prev => ({
            ...prev,
            attributes: upsertByUuid(prev.attributes ?? [], attribute),
        }));
        console.log(
            `${LOG_PREFIX} Replaced attribute uuid="${attribute.uuid}"`,
        );
        toastStore.success(
            "Attribute saved",
            "Changes on the attribute successfully saved.",
        );
        return { error: null, data: data };
    }

    // =========================================================================
    // ENUM ENTRY OPERATIONS
    // =========================================================================

    async function addEnumEntry(
        datasetName: string,
        graphURI: string,
        classUUID: string,
        enumEntry: EnumEntryDto,
    ): Promise<Result<string>> {
        console.log(
            `${LOG_PREFIX} Adding enum entry to class classUUID="${classUUID}"`,
        );

        const { data, error } = await createEnumEntry({
            path: { datasetName, graphURI, classUUID },
            body: enumEntry,
        });
        if (error) {
            console.error(
                `${LOG_PREFIX} Could not add enum entry`,
                await describeError(error),
            );
            toastStore.error(
                "Add enum entry failed.",
                "Enum entry could not be created.",
            );
            return { error };
        }

        const uuid = data ?? enumEntry.uuid;
        const stored: EnumEntryDto = { ...enumEntry, uuid };

        const key = makeGraphKey(datasetName, graphURI);
        mutateClassInPlace(key, classUUID, prev => ({
            ...prev,
            enumEntries: upsertByUuid(prev.enumEntries ?? [], stored),
        }));
        console.log(
            `${LOG_PREFIX} Added enum entry uuid="${uuid}" to class classUUID="${classUUID}"`,
        );
        toastStore.success(
            "Enum entry created",
            "Enum entry was added successfully.",
        );
        return { error: null, data: data };
    }

    async function replaceExistingEnumEntry(
        datasetName: string,
        graphURI: string,
        classUUID: string,
        enumEntry: EnumEntryDto,
    ): Promise<Result<string>> {
        if (!enumEntry.uuid) {
            const err = new Error("enumEntry.uuid is required");
            console.error(
                `${LOG_PREFIX} replaceEnumEntry validation failed`,
                err,
            );
            return { error: err };
        }

        console.log(
            `${LOG_PREFIX} Replacing enum entry uuid="${enumEntry.uuid}"`,
        );

        const { data, error } = await replaceEnumEntry({
            path: {
                datasetName,
                graphURI,
                classUUID,
                enumEntryUUID: enumEntry.uuid,
            },
            body: enumEntry,
        });
        if (error) {
            console.error(
                `${LOG_PREFIX} Could not replace enum entry uuid="${enumEntry.uuid}"`,
                await describeError(error),
            );
            toastStore.error("Save failed", "Enum entry could not be saved.");
            return { error };
        }

        const key = makeGraphKey(datasetName, graphURI);
        mutateClassInPlace(key, classUUID, prev => ({
            ...prev,
            enumEntries: upsertByUuid(prev.enumEntries ?? [], enumEntry),
        }));
        console.log(
            `${LOG_PREFIX} Replaced enum entry uuid="${enumEntry.uuid}"`,
        );
        toastStore.success("Enum entry saved", "Changes were applied.");
        return { error: null, data: data };
    }

    // =========================================================================
    // ASSOCIATION OPERATIONS
    // =========================================================================

    async function addAssociationPair(
        datasetName: string,
        graphURI: string,
        classUUID: string,
        pair: AssociationPairDto,
    ): Promise<Result<AssociationUuids>> {
        console.log(
            `${LOG_PREFIX} Adding association pair to class classUUID="${classUUID}"`,
        );

        const { data, error } = await createAssociation({
            path: { datasetName, graphURI, classUUID },
            body: pair,
        });
        if (error) {
            console.error(
                `${LOG_PREFIX} Could not add association pair`,
                await describeError(error),
            );
            toastStore.error(
                "Add association failed",
                "Association could not be created.",
            );
            return { error };
        }

        const enriched: AssociationPairDto = {
            from: { ...pair.from, uuid: data?.fromUUID ?? pair.from?.uuid },
            to: { ...pair.to, uuid: data?.toUUID ?? pair.to?.uuid },
        };

        const key = makeGraphKey(datasetName, graphURI);
        mutateClassInPlace(key, classUUID, prev => ({
            ...prev,
            associationPairs: upsertAssociationPair(
                prev.associationPairs ?? [],
                enriched,
            ),
        }));
        console.log(
            `${LOG_PREFIX} Added association pair to class classUUID="${classUUID}"`,
        );
        toastStore.success(
            "Association added",
            "Association was added successfully.",
        );
        return { error: null, data: data ?? undefined };
    }

    async function replaceAssociationPair(
        datasetName: string,
        graphURI: string,
        classUUID: string,
        pair: AssociationPairDto,
    ): Promise<Result<AssociationUuids>> {
        const associationUUID = pair.from?.uuid;
        if (!associationUUID) {
            const err = new Error("pair.from.uuid is required");
            console.error(
                `${LOG_PREFIX} replaceAssociationPair validation failed`,
                err,
            );
            return { error: err };
        }

        console.log(
            `${LOG_PREFIX} Replacing association pair uuid="${associationUUID}"`,
        );

        const { data, error } = await replaceAssociation({
            path: { datasetName, graphURI, classUUID, associationUUID },
            body: pair,
        });
        if (error) {
            console.error(
                `${LOG_PREFIX} Could not replace association pair uuid="${associationUUID}"`,
                await describeError(error),
            );
            toastStore.error(
                "Save failed",
                "Association could not be replaced.",
            );
            return { error };
        }

        const updatedPair: AssociationPairDto = {
            from: { ...pair.from, uuid: data?.fromUUID ?? pair.from?.uuid },
            to: { ...pair.to, uuid: data?.toUUID ?? pair.to?.uuid },
        };

        const key = makeGraphKey(datasetName, graphURI);
        mutateClassInPlace(key, classUUID, prev => {
            const filtered = (prev.associationPairs ?? []).filter(
                p => p.from?.uuid !== associationUUID,
            );
            return {
                ...prev,
                associationPairs: upsertAssociationPair(filtered, updatedPair),
            };
        });
        console.log(
            `${LOG_PREFIX} Replaced association pair uuid="${associationUUID}"`,
        );
        toastStore.success("Association saved", "Changes were applied.");

        return { error: null, data: data ?? undefined };
    }

    function invalidateGraph(datasetName: string, graphURI: string) {
        const key = makeGraphKey(datasetName, graphURI);
        const pendingPrefix = `${key}::`;
        console.log(`${LOG_PREFIX} Invalidating graph cache key="${key}"`);
        update(s => {
            const byGraph = new Map(s.byGraph);
            byGraph.delete(key);
            const pendingDetails = new Map(s.pendingDetails);
            for (const k of pendingDetails.keys()) {
                if (k.startsWith(pendingPrefix)) pendingDetails.delete(k);
            }
            return { ...s, byGraph, pendingDetails };
        });
    }

    function invalidateDataset(datasetName: string) {
        const prefix = `${datasetName}::`;
        console.log(
            `${LOG_PREFIX} Invalidating dataset cache dataset="${datasetName}"`,
        );
        update(s => {
            const byGraph = new Map(s.byGraph);
            for (const k of byGraph.keys()) {
                if (k.startsWith(prefix)) byGraph.delete(k);
            }
            return { ...s, byGraph };
        });
    }

    return {
        subscribe,

        // getters
        getClasses,
        getClassInfo,

        // class-level mutations
        addClass: addNewClass,
        replaceClass: replaceExistingClass,
        extendClass: extendExistingClass,
        saveCopyClass: pasteCopiedClasses,

        // attribute mutations
        addAttribute,
        replaceAttribute: replaceExistingAttribute,

        // enum entry mutations
        addEnumEntry,
        replaceEnumEntry: replaceExistingEnumEntry,

        // association mutations
        addAssociationPair,
        replaceAssociationPair: replaceAssociationPair,

        // invalidation
        invalidateGraph,
        invalidateDataset,
    };
}
export { createClassStore };
