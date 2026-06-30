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

import { describeError } from "./StoreLogging";
import {
    // class list & details
    getClassList,
    getClassInformation,
    // class-level mutations
    addClass,
    replaceClass,
    deleteClass,
    extendClass,
    copyClass,
    // attribute mutations
    createAttribute,
    replaceAttribute,
    // association mutations
    createAssociation,
    replaceAssociation,
    // enum entry mutations
    createEnumEntry,
    replaceEnumEntry,
    // generic resource deletion
    deleteResources,
    // types
    AddNewClassRequest,
    AssociationPairDto,
    AttributeDto,
    ClassDto,
    ClassUmlAdaptedDto,
    CopyClassRequestDto,
    CopyClassResponseDto,
    EnumEntryDto,
    ResourceDeleteRequest,
    AssociationUuids,
} from "../api/generated";
import { toastStore } from "../eventhandling/toastStore.svelte.js";

// Two cache slots per graph: the list endpoint may be called with or without
// external classes; keep both so toggling the flag does not invalidate the
// other one.
type Variant = "all" | "internalOnly";

type VariantState = {
    data: ClassUmlAdaptedDto[] | null;
    fetchedAt: number | null;
    pending: Promise<void> | null;
    error: unknown;
};

type GraphClassState = {
    all: VariantState;
    internalOnly: VariantState;
};

type ClassesState = {
    byGraph: Map<GraphKey, GraphClassState>;
};

type GraphKey = `${string}::${string}`;

type Result<T = void> = { error: unknown; data?: T };

const LOG_PREFIX = "[classStore]";

export const classStore = createClassStore();

function makeKey(datasetName: string, graphURI: string): GraphKey {
    return `${datasetName}::${graphURI}`;
}

function createEmptyVariantState(): VariantState {
    return { data: null, fetchedAt: null, pending: null, error: null };
}

function createEmptyGraphState(): GraphClassState {
    return {
        all: createEmptyVariantState(),
        internalOnly: createEmptyVariantState(),
    };
}

// ----- Helpers -----

function getId(c: ClassUmlAdaptedDto): string | undefined {
    return c.uuid;
}

// The list endpoint returns classes without details: `attributes`,
// `enumEntries` and `associationPairs` are omitted. The detail endpoint fills
// them. Treat the presence of any array as "details are loaded".
function hasDetails(c: ClassUmlAdaptedDto | null | undefined): boolean {
    if (!c) return false;
    return (
        Array.isArray(c.attributes) ||
        Array.isArray(c.enumEntries) ||
        Array.isArray(c.associationPairs)
    );
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
    const store = writable<ClassesState>({ byGraph: new Map() });
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

    // ----- Loaders -----

    async function load(
        datasetName: string,
        graphURI: string,
        includeExternal = false,
        force = false,
    ) {
        if (!datasetName || !graphURI) return;

        const key = makeKey(datasetName, graphURI);
        const variant: Variant = includeExternal ? "all" : "internalOnly";
        const variantState = getGraphState(get(store), key)[variant];

        if (!force && variantState.data !== null) return;
        if (variantState.pending !== null) return variantState.pending;

        console.log(
            `${LOG_PREFIX} Loading classes for dataset="${datasetName}", graph="${graphURI}", includeExternal=${includeExternal}, force=${force}`,
        );

        const promise = (async () => {
            try {
                const { data, error } = await getClassList({
                    path: { datasetName, graphURI },
                    query: { includeExternalClasses: includeExternal },
                });

                if (error) {
                    console.error(
                        `${LOG_PREFIX} Failed to load classes for dataset="${datasetName}", graph="${graphURI}"`,
                        await describeError(error),
                    );
                    update(s =>
                        setVariant(s, key, variant, {
                            ...getGraphState(s, key)[variant],
                            pending: null,
                            error,
                        }),
                    );
                    return;
                }

                update(s =>
                    setVariant(s, key, variant, {
                        data: data ?? [],
                        fetchedAt: Date.now(),
                        pending: null,
                        error: null,
                    }),
                );

                console.log(
                    `${LOG_PREFIX} Loaded ${(data ?? []).length} classes for dataset="${datasetName}", graph="${graphURI}", variant="${variant}"`,
                );
            } catch (err) {
                console.error(
                    `${LOG_PREFIX} Unexpected error while loading classes for dataset="${datasetName}", graph="${graphURI}"`,
                    err,
                );
                update(s =>
                    setVariant(s, key, variant, {
                        ...getGraphState(s, key)[variant],
                        pending: null,
                        error: err,
                    }),
                );
            }
        })();

        update(s =>
            setVariant(s, key, variant, {
                ...getGraphState(s, key)[variant],
                pending: promise,
            }),
        );

        return promise;
    }

    async function loadClassInfo(
        datasetName: string,
        graphURI: string,
        classUUID: string,
        force = false,
    ): Promise<Result<ClassUmlAdaptedDto>> {
        if (!datasetName || !graphURI || !classUUID) return { error: null };

        const key = makeKey(datasetName, graphURI);

        if (!force) {
            const current = getGraphState(get(store), key);
            const existing =
                findInVariant(current.all.data, classUUID) ??
                findInVariant(current.internalOnly.data, classUUID);
            if (hasDetails(existing)) return { error: null, data: existing };
        }

        console.log(
            `${LOG_PREFIX} Loading class details for classUUID="${classUUID}" in dataset="${datasetName}", graph="${graphURI}"`,
        );

        const { data, error } = await getClassInformation({
            path: { datasetName, graphURI, classUUID },
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
            return { error };
        }

        update(s => {
            const current = getGraphState(s, key);
            const mergeInto = (variant: VariantState): VariantState => {
                if (!variant.data) return variant;
                return {
                    ...variant,
                    data: upsertClass(variant.data, data),
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

        return { error: null, data };
    }

    // ----- Getters -----

    function getClasses(
        datasetName: string,
        graphURI: string,
        includeExternal = false,
    ): ClassUmlAdaptedDto[] | null {
        const variant: Variant = includeExternal ? "all" : "internalOnly";
        return getGraphState(get(store), makeKey(datasetName, graphURI))[
            variant
        ].data;
    }

    function getClass(
        datasetName: string,
        graphURI: string,
        classUUID: string,
    ): ClassUmlAdaptedDto | null {
        const state = getGraphState(get(store), makeKey(datasetName, graphURI));
        return (
            findInVariant(state.all.data, classUUID) ??
            findInVariant(state.internalOnly.data, classUUID) ??
            null
        );
    }

    function getClassInfo(
        datasetName: string,
        graphURI: string,
        classUUID: string,
    ): ClassUmlAdaptedDto | null {
        const c = getClass(datasetName, graphURI, classUUID);
        return hasDetails(c) ? c : null;
    }

    function getAttributes(
        datasetName: string,
        graphURI: string,
        classUUID: string,
    ): AttributeDto[] | null {
        return (
            getClassInfo(datasetName, graphURI, classUUID)?.attributes ?? null
        );
    }

    function getEnumEntries(
        datasetName: string,
        graphURI: string,
        classUUID: string,
    ): EnumEntryDto[] | null {
        return (
            getClassInfo(datasetName, graphURI, classUUID)?.enumEntries ?? null
        );
    }

    function getAssociationPairs(
        datasetName: string,
        graphURI: string,
        classUUID: string,
    ): AssociationPairDto[] | null {
        return (
            getClassInfo(datasetName, graphURI, classUUID)?.associationPairs ??
            null
        );
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
        toastStore.success("Klasse erstellt", "Class successfully created.");
        return { error: null, data: data ?? undefined };
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

        const key = makeKey(datasetName, graphURI);
        mutateClassInPlace(key, classUUID, prev => ({
            ...prev,
            ...cls,
            uuid: classUUID,
        }));
        console.log(`${LOG_PREFIX} Replaced class classUUID="${classUUID}"`);
        toastStore.success("Class saved", "Changes were applied.");
        return { error: null };
    }

    async function deleteExistingClass(
        datasetName: string,
        graphURI: string,
        classUUID: string,
    ): Promise<Result> {
        console.log(`${LOG_PREFIX} Deleting class classUUID="${classUUID}"`);

        const { error } = await deleteClass({
            path: { datasetName, graphURI, classUUID },
        });
        if (error) {
            console.error(
                `${LOG_PREFIX} Could not delete class classUUID="${classUUID}"`,
                await describeError(error),
            );
            toastStore.error("Delete failed", "Class could not be deleted.");
            return { error };
        }

        removeClassLocally(datasetName, graphURI, classUUID);
        console.log(`${LOG_PREFIX} Deleted class classUUID="${classUUID}"`);
        toastStore.success("Class deleted", "Class was deleted.");
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

    async function copyExistingClass(
        datasetName: string,
        graphURI: string,
        classUUID: string,
        request: CopyClassRequestDto,
    ): Promise<Result<CopyClassResponseDto>> {
        console.log(
            `${LOG_PREFIX} Copying class classUUID="${classUUID}" from dataset="${datasetName}", graph="${graphURI}"`,
        );

        const { data, error } = await copyClass({
            path: { datasetName, graphURI, classUUID },
            body: request,
        });
        if (error) {
            console.error(
                `${LOG_PREFIX} Could not copy class classUUID="${classUUID}"`,
                await describeError(error),
            );
            toastStore.error("Copy failed", "Class could not be copied.");
            return { error };
        }

        if (request.targetDatasetName && request.targetGraphURI) {
            invalidateGraph(request.targetDatasetName, request.targetGraphURI);
        }
        console.log(`${LOG_PREFIX} Copied class classUUID="${classUUID}"`);
        toastStore.success("Class copied", "Class was successfully copied.");
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

        const key = makeKey(datasetName, graphURI);
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
    ): Promise<Result> {
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

        const { error } = await replaceAttribute({
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

        const key = makeKey(datasetName, graphURI);
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
        return { error: null };
    }

    async function deleteAttribute(
        datasetName: string,
        graphURI: string,
        classUUID: string,
        attributeUUID: string,
        action: ResourceDeleteRequest["action"] = "DELETE",
    ): Promise<Result> {
        console.log(`${LOG_PREFIX} Deleting attribute uuid="${attributeUUID}"`);

        const { error } = await deleteResources({
            path: { datasetName, graphURI },
            body: [{ uuid: attributeUUID, action }],
        });
        if (error) {
            console.error(
                `${LOG_PREFIX} Could not delete attribute uuid="${attributeUUID}"`,
                await describeError(error),
            );
            toastStore.error(
                "Delete failed",
                "Attribute could not be deleted.",
            );
            return { error };
        }

        const key = makeKey(datasetName, graphURI);
        mutateClassInPlace(key, classUUID, prev => ({
            ...prev,
            attributes: (prev.attributes ?? []).filter(
                a => a.uuid !== attributeUUID,
            ),
        }));
        console.log(`${LOG_PREFIX} Deleted attribute uuid="${attributeUUID}"`);
        toastStore.success(
            "Attribute deleted",
            "Attribute was removed successfully.",
        );
        return { error: null };
    }

    // =========================================================================
    // ENUM ENTRY OPERATIONS
    // =========================================================================

    async function addEnumEntry(
        datasetName: string,
        graphURI: string,
        classUUID: string,
        enumEntry: EnumEntryDto,
    ): Promise<Result> {
        console.log(
            `${LOG_PREFIX} Adding enum entry to class classUUID="${classUUID}"`,
        );

        const { error } = await createEnumEntry({
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

        const key = makeKey(datasetName, graphURI);
        mutateClassInPlace(key, classUUID, prev => ({
            ...prev,
            enumEntries: upsertByUuid(prev.enumEntries ?? [], enumEntry),
        }));
        console.log(
            `${LOG_PREFIX} Added enum entry to class classUUID="${classUUID}"`,
        );
        toastStore.success(
            "Enum entry created",
            "Enum entry was added successfully.",
        );
        return { error: null };
    }

    async function replaceExistingEnumEntry(
        datasetName: string,
        graphURI: string,
        classUUID: string,
        enumEntry: EnumEntryDto,
    ): Promise<Result> {
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

        const { error } = await replaceEnumEntry({
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

        const key = makeKey(datasetName, graphURI);
        mutateClassInPlace(key, classUUID, prev => ({
            ...prev,
            enumEntries: upsertByUuid(prev.enumEntries ?? [], enumEntry),
        }));
        console.log(
            `${LOG_PREFIX} Replaced enum entry uuid="${enumEntry.uuid}"`,
        );
        toastStore.success("Enum entry saved", "Changes were applied.");
        return { error: null };
    }

    async function deleteEnumEntry(
        datasetName: string,
        graphURI: string,
        classUUID: string,
        enumEntryUUID: string,
        action: ResourceDeleteRequest["action"] = "DELETE",
    ): Promise<Result> {
        console.log(
            `${LOG_PREFIX} Deleting enum entry uuid="${enumEntryUUID}"`,
        );

        const { error } = await deleteResources({
            path: { datasetName, graphURI },
            body: [{ uuid: enumEntryUUID, action }],
        });
        if (error) {
            console.error(
                `${LOG_PREFIX} Could not delete enum entry uuid="${enumEntryUUID}"`,
                await describeError(error),
            );
            toastStore.error(
                "Delete failed",
                "Enum entry could not be deleted.",
            );
            return { error };
        }

        const key = makeKey(datasetName, graphURI);
        mutateClassInPlace(key, classUUID, prev => ({
            ...prev,
            enumEntries: (prev.enumEntries ?? []).filter(
                e => e.uuid !== enumEntryUUID,
            ),
        }));
        console.log(`${LOG_PREFIX} Deleted enum entry uuid="${enumEntryUUID}"`);
        toastStore.success(
            "Enum entry deleted",
            "Enum entry was successfully deleted.",
        );
        return { error: null };
    }

    // =========================================================================
    // ASSOCIATION OPERATIONS
    // =========================================================================

    async function addAssociationPair(
        datasetName: string,
        graphURI: string,
        classUUID: string,
        pair: AssociationPairDto,
    ): Promise<Result<AssociationPairDto>> {
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

        const key = makeKey(datasetName, graphURI);
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
        return { error: null, data: enriched };
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

        const enriched: AssociationPairDto = {
            from: { ...pair.from, uuid: data?.fromUUID ?? pair.from?.uuid },
            to: { ...pair.to, uuid: data?.toUUID ?? pair.to?.uuid },
        };

        const key = makeKey(datasetName, graphURI);
        mutateClassInPlace(key, classUUID, prev => {
            const filtered = (prev.associationPairs ?? []).filter(
                p => p.from?.uuid !== associationUUID,
            );
            return {
                ...prev,
                associationPairs: upsertAssociationPair(filtered, enriched),
            };
        });
        console.log(
            `${LOG_PREFIX} Replaced association pair uuid="${associationUUID}"`,
        );
        toastStore.success("Association saved", "Changes were applied.");
        return { error: null, data: data };
    }

    async function deleteAssociationPair(
        datasetName: string,
        graphURI: string,
        classUUID: string,
        associationUUID: string,
        action: ResourceDeleteRequest["action"] = "DELETE",
    ): Promise<Result> {
        console.log(
            `${LOG_PREFIX} Deleting association pair uuid="${associationUUID}"`,
        );

        const { error } = await deleteResources({
            path: { datasetName, graphURI },
            body: [{ uuid: associationUUID, action }],
        });
        if (error) {
            console.error(
                `${LOG_PREFIX} Could not delete association pair uuid="${associationUUID}"`,
                await describeError(error),
            );
            toastStore.error(
                "Delete failed",
                "Association could not be deleted.",
            );
            return { error };
        }

        const key = makeKey(datasetName, graphURI);
        mutateClassInPlace(key, classUUID, prev => ({
            ...prev,
            associationPairs: (prev.associationPairs ?? []).filter(
                p => p.from?.uuid !== associationUUID,
            ),
        }));
        console.log(
            `${LOG_PREFIX} Deleted association pair uuid="${associationUUID}"`,
        );
        toastStore.success(
            "Association deleted",
            "Association successfully deleted.",
        );
        return { error: null };
    }

    // =========================================================================
    // LOCAL-ONLY HELPERS & INVALIDATION
    // =========================================================================

    function removeClassLocally(
        datasetName: string,
        graphURI: string,
        classUUID: string,
    ) {
        if (!datasetName || !graphURI || !classUUID) return;
        const key = makeKey(datasetName, graphURI);

        update(s => {
            const current = s.byGraph.get(key);
            if (!current) return s;

            const dropFrom = (variant: VariantState): VariantState => {
                if (!variant.data) return variant;
                return {
                    ...variant,
                    data: variant.data.filter(c => getId(c) !== classUUID),
                    fetchedAt: Date.now(),
                    error: null,
                };
            };

            return setGraphState(s, key, {
                all: dropFrom(current.all),
                internalOnly: dropFrom(current.internalOnly),
            });
        });
    }

    function invalidateGraph(datasetName: string, graphURI: string) {
        const key = makeKey(datasetName, graphURI);
        console.log(`${LOG_PREFIX} Invalidating graph cache key="${key}"`);
        update(s => {
            const byGraph = new Map(s.byGraph);
            byGraph.delete(key);
            return { ...s, byGraph };
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

    function invalidateAll() {
        console.log(`${LOG_PREFIX} Invalidating all class caches`);
        update(() => ({ byGraph: new Map() }));
    }

    return {
        subscribe,

        // loaders
        load,
        loadClassInfo,

        // getters
        getClasses,
        getClassInfo,
        getAttributes,
        getEnumEntries,
        getAssociationPairs,

        // class-level mutations
        addClass: addNewClass,
        replaceClass: replaceExistingClass,
        deleteClass: deleteExistingClass,
        extendClass: extendExistingClass,
        copyClass: copyExistingClass,

        // attribute mutations
        addAttribute,
        replaceAttribute: replaceExistingAttribute,
        deleteAttribute,

        // enum entry mutations
        addEnumEntry,
        replaceEnumEntry: replaceExistingEnumEntry,
        deleteEnumEntry,

        // association mutations
        addAssociationPair,
        replaceAssociationPair: replaceAssociationPair,
        deleteAssociationPair,

        // invalidation
        invalidateGraph,
        invalidateDataset,
        invalidateAll,
    };
}
