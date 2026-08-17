<!--
  -    Copyright (c) 2024-2026 SOPTIM AG
  -
  -    Licensed under the Apache License, Version 2.0 (the "License");
  -    you may not use this file except in compliance with the License.
  -    You may obtain a copy of the License at
  -
  -        http://www.apache.org/licenses/LICENSE-2.0
  -
  -    Unless required by applicable law or agreed to in writing, software
  -    distributed under the License is distributed on an "AS IS" BASIS,
  -    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  -    See the License for the specific language governing permissions and
  -    limitations under the License.
  -
  -->

<script>
    import { onDestroy, onMount, setContext } from "svelte";
    import { Pane, Splitpanes } from "svelte-splitpanes";

    import { isReadOnly } from "$lib/api/apiDatasetUtils.js";
    import { BackendConnection } from "$lib/api/backend.js";
    import ButtonControl from "$lib/components/ButtonControl.svelte";
    import LoadingSpinner from "$lib/components/LoadingSpinner.svelte";
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import {
        eventStack,
        EventType,
    } from "$lib/eventhandling/closeEventManager.svelte.js";
    import { toastStore } from "$lib/eventhandling/toastStore.svelte.js";
    import {
        findSuperClass,
        mapClassDtoToReactiveClass,
        mapSuperClassesToInherited,
    } from "$lib/models/reactive/mapper/map-dto-to-reactive-object.js";
    import { adoptUnsavedClassChanges } from "$lib/models/reactive/utils/adopt-model-changes-utils.js";
    import {
        ClassType,
        editorState,
        forceReloadTrigger,
        multiSelectState,
    } from "$lib/sharedState.svelte.js";

    import {
        getClasses,
        getDataTypes,
        getNamespaces,
        getPackages,
        getStereotypes,
    } from "./fetch-class-editor-context.js";
    import ShaclPropertySpecificDialog from "../../shacl/SHACLPropertySpecificDialog.svelte";
    import Associations from "./components/associations/Associations.svelte";
    import Attributes from "./components/attributes/Attributes.svelte";
    import ClassEditorButtons from "./components/ClassEditorButtons.svelte";
    import Comment from "./components/Comment.svelte";
    import EnumEntries from "./components/enum-entries/EnumEntries.svelte";
    import Label from "./components/Label.svelte";
    import Namespace from "./components/Namespace.svelte";
    import Package from "./components/Package.svelte";
    import Stereotypes from "./components/stereotypes/Stereotypes.svelte";
    import SuperClass from "./components/SuperClass.svelte";

    const { datasetName, graphUri, classUuid } = $props();

    const enumerationStereotype =
        "http://iec.ch/TC57/NonStandard/UML#enumeration";
    const bec = new BackendConnection(fetch, PUBLIC_BACKEND_URL);

    const context = {
        namespaces: [],
        stereotypes: [],
        datatypes: [],
        packages: [],
        classes: [],
        superClass: null,
        targetClassInfos: [],
    };

    let isDatasetReadOnly = $state(false);

    let reactiveClass = $state();

    let externalClass = $state(null);

    let creatingClass = $state(false);

    let inheritedAttributes = $state([]);

    let inheritedAssociations = $state([]);

    let inheritedEnumEntries = $state([]);

    let loadingContext = $state(true);

    let loadingClass = $state(true);

    const propertyShaclRulesDialog = $state({
        showDialog: false,
        property: null,
        classUuidOverride: null,
    });

    let showDiscardSaveConfirmDialog = $state(false);

    let datasetOfClassToOpenNext = $state(null);
    let graphOfClassToOpenNext = $state(null);
    let classToOpenNext = $state(null);
    let classTypeOfClassToOpenNext = $state(null);

    let pendingAction = $state(null);

    let isEnum = $derived(
        reactiveClass?.stereotypes.contains(enumerationStereotype),
    );

    $effect(() => {
        editorState.selectedClass.subscribe();
        forceReloadTrigger.subscribe();

        const cancellation = { cancelled: false };
        loadingContext = true;
        loadingClass = true;
        // Clearing this up front keeps the panel of the previously opened class from showing
        // through the loading overlay while the newly selected one is fetched.
        externalClass = null;
        (async () => {
            let res = await bec.getClassInfo(
                datasetName,
                graphUri,
                classUuid,
                true,
            );
            let resText = await res.text();
            if (cancellation.cancelled) return;
            if (!resText) {
                return closeClassEditor({
                    datasetName: datasetName,
                    graphUri: graphUri,
                    classUuid: null,
                });
            }
            let classData;
            try {
                classData = JSON.parse(resText);
            } catch (e) {
                console.error(
                    "Failed to parse class data for class UUID",
                    classUuid,
                    "in dataset",
                    datasetName,
                    "and graph",
                    graphUri,
                    ":",
                    e,
                );
                return closeClassEditor({
                    datasetName: datasetName,
                    graphUri: graphUri,
                    classUuid: null,
                });
            }
            const readOnly = await isReadOnly(datasetName);
            if (cancellation.cancelled) return;
            isDatasetReadOnly = readOnly;
            if (classData.external) {
                reactiveClass = undefined;
                externalClass = classData;
                loadingContext = false;
                loadingClass = false;
                return;
            }
            await loadContext(cancellation);
            await loadReactiveClass(cancellation, classData);
        })();

        return () => {
            cancellation.cancelled = true;
        };
    });

    $effect(async () => {
        editorState.selectedDiagram.subscribe();
        forceReloadTrigger.subscribe();
        isDatasetReadOnly = await isReadOnly(datasetName);
    });

    onMount(() => {
        eventStack.addEvent(closeClassEditor, EventType.CLASS_EDITOR);
        eventStack.registerActionGuard(withUnsavedChangesCheck);
    });

    onDestroy(() => {
        eventStack.removeEvent(closeClassEditor);
        eventStack.unregisterActionGuard(withUnsavedChangesCheck);
    });

    function withUnsavedChangesCheck(action) {
        if (reactiveClass?.isModified) {
            pendingAction = action;
            showDiscardSaveConfirmDialog = true;
            return;
        }
        return action();
    }

    export function closeClassEditor(
        {
            datasetName = null,
            graphUri = null,
            classUuid = null,
            classType = null,
        } = {
            datasetName: null,
            graphUri: null,
            classUuid: null,
            classType: null,
        },
    ) {
        if (!showDiscardSaveConfirmDialog && reactiveClass?.isModified) {
            showDiscardSaveConfirmDialog = true;
            datasetOfClassToOpenNext = datasetName;
            graphOfClassToOpenNext = graphUri;
            classToOpenNext = classUuid;
            classTypeOfClassToOpenNext = classType;
            return;
        }
        editorState.selectedClassDataset.updateValue(datasetName);
        editorState.selectedClassGraph.updateValue(graphUri);
        editorState.selectedClass.updateValue({
            type: classType,
            id: classUuid,
        });
    }

    /**
     * Creates the class for a referenced only resource. Its namespace comes from the referenced
     * uri and its package from the diagram it is shown in, so nothing has to be asked for. The
     * backend reuses the uuid of the referenced resource, so the class keeps its identity and its
     * layout data.
     */
    async function createReferencedClass() {
        creatingClass = true;
        try {
            const res = await bec.postClass(datasetName, graphUri, {
                packageDTO: await packageOfCurrentDiagram(),
                classURIPrefix: externalClass.prefix,
                className: externalClass.label,
            });
            if (!res.ok) {
                toastStore.error(
                    "Create failed",
                    `Could not create class "${externalClass.label}".`,
                );
                return;
            }
            toastStore.success(
                "Class created",
                `"${externalClass.label}" was added.`,
            );
            forceReloadTrigger.trigger();
        } catch (e) {
            console.error("failed to create referenced class:", e);
            toastStore.error(
                "Create failed",
                "An unexpected error occurred while creating the class.",
            );
        } finally {
            creatingClass = false;
        }
    }

    async function packageOfCurrentDiagram() {
        const diagramId = editorState.selectedDiagram.getProperty("id");
        if (!diagramId || diagramId === "default") {
            return null;
        }
        const packages = await getPackages(datasetName, graphUri);
        return packages.find(pkg => pkg.uuid === diagramId) ?? null;
    }

    async function loadContext(cancellation) {
        const [classes, packages, datatypes, stereotypes, namespaces] =
            await Promise.all([
                getClasses(datasetName, graphUri),
                getPackages(datasetName, graphUri),
                getDataTypes(datasetName, graphUri),
                getStereotypes(datasetName, graphUri),
                getNamespaces(datasetName),
            ]);
        if (cancellation.cancelled) return;
        context.classes = classes;
        context.packages = packages;
        context.datatypes = datatypes;
        context.stereotypes = stereotypes;
        context.namespaces = namespaces;
        loadingContext = false;
        editorState.selectedContext.trigger();
    }

    async function loadReactiveClass(cancelled, classDTO) {
        if (cancelled.cancelled) return;
        context.superClass = findSuperClass(context.classes, classDTO);
        const newReactiveClass = mapClassDtoToReactiveClass(
            classDTO,
            context,
            uuid => context.targetClassInfos.find(cls => cls.uuid === uuid),
        );
        reactiveClass = adoptUnsavedClassChanges(
            newReactiveClass,
            reactiveClass,
        );
        const inherited = mapSuperClassesToInherited(
            classDTO.superClass,
            context.classes,
        );
        inheritedAttributes = inherited.attributeGroups;
        inheritedAssociations = inherited.associationGroups;
        inheritedEnumEntries = inherited.enumEntryGroups;
        loadingClass = false;

        const targetUuids = [
            ...new Set(
                reactiveClass.associations.values
                    .map(assoc => assoc.target.value)
                    .filter(uuid => uuid != null),
            ),
        ];

        let targetClassInfos = await Promise.all(
            targetUuids.map(async uuid => {
                const res = await bec.getClassInfo(datasetName, graphUri, uuid);
                if (!res || !res.ok) return null;
                const text = await res.text();
                if (!text) return null;
                return JSON.parse(text);
            }),
        );
        if (cancelled.cancelled) return;
        context.targetClassInfos = targetClassInfos.filter(Boolean);
    }

    function openPropertySHACLRulesDialog(property, classUuidOverride = null) {
        propertyShaclRulesDialog.property = property;
        propertyShaclRulesDialog.classUuidOverride = classUuidOverride;
        propertyShaclRulesDialog.showDialog = true;
    }

    setContext("classEditor", {
        get datasetName() {
            return datasetName;
        },
        get graphUri() {
            return graphUri;
        },
        get readonly() {
            return isDatasetReadOnly;
        },
        get namespaces() {
            return context.namespaces;
        },
        get stereotypes() {
            return context.stereotypes;
        },
        get datatypes() {
            return context.datatypes;
        },
        get classes() {
            return context.classes;
        },
        get packages() {
            return context.packages;
        },
        get reactiveClass() {
            return reactiveClass;
        },
        get targetClassInfos() {
            return context.targetClassInfos;
        },
        get backendConnection() {
            return bec;
        },
        get getClassByUuid() {
            return function (uuid) {
                const cls = context.classes.find(cls => cls.uuid === uuid);
                if (cls) {
                    return cls;
                }
                return context.superClass?.uuid === uuid
                    ? context.superClass
                    : undefined;
            };
        },
        get getTargetClassInfoByUuid() {
            return function (uuid) {
                return context.targetClassInfos.find(cls => cls.uuid === uuid);
            };
        },
        get getSubstitutedNamespace() {
            return function (namespace) {
                const namespaceObj = context.namespaces.find(
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
                return context.datatypes.find(
                    dt => dt.prefix + dt.label === uri,
                );
            };
        },
        get getPackageByUuid() {
            return function (uuid) {
                return context.packages.find(pkg => pkg.uuid === uuid);
            };
        },
        addTargetClassInfo(classInfo) {
            context.targetClassInfos = [...context.targetClassInfos, classInfo];
        },
        openClass(classUuidToOpen) {
            if (!classUuidToOpen) return;
            if (!reactiveClass?.isModified) {
                multiSelectState.clear();
            }
            closeClassEditor({
                datasetName,
                graphUri,
                classUuid: classUuidToOpen,
                classType: ClassType.SINGLE_CLASS,
            });
        },
    });
</script>

<div class="relative h-full w-full">
    <Splitpanes
        theme="opencgmes-theme"
        horizontal
        class="bg-window-background h-full"
    >
        {#if externalClass}
            <Pane
                size={100}
                class="bg-window-background z-2 size-full rounded-xs border-none"
            >
                <div
                    class="text-default-text flex size-full flex-col items-center justify-center gap-3 p-4 text-center"
                >
                    <span class="text-lg font-bold">{externalClass.label}</span>
                    <span class="text-sm opacity-70">
                        This class is referenced here but not defined in this
                        schema.
                    </span>
                    {#if !isDatasetReadOnly}
                        <ButtonControl
                            callOnClick={createReferencedClass}
                            disabled={creatingClass}
                        >
                            Create class
                        </ButtonControl>
                    {/if}
                </div>
            </Pane>
        {:else if reactiveClass}
            <Pane
                size={75}
                class="bg-window-background z-2 size-full rounded-xs border-none"
            >
                <div class="flex size-full flex-col p-2">
                    <ClassEditorButtons
                        {reactiveClass}
                        bind:showDiscardSaveConfirmDialog
                        bind:pendingAction
                        {datasetOfClassToOpenNext}
                        {graphOfClassToOpenNext}
                        {classToOpenNext}
                        {classTypeOfClassToOpenNext}
                        {closeClassEditor}
                    />
                    <div
                        class="border-border mt-2 size-full overflow-y-scroll rounded-sm border-t"
                    >
                        <div
                            class="mt-1 flex max-h-max flex-col justify-between"
                        >
                            <table
                                class="border-separate border-spacing-x-1.5 border-spacing-y-1"
                            >
                                <tbody>
                                    <Label label={reactiveClass.label} />
                                    <Namespace
                                        namespace={reactiveClass.namespace}
                                    />
                                    <Package pack={reactiveClass.package} />
                                    <SuperClass
                                        superClass={reactiveClass.superClass}
                                    />
                                    <Stereotypes
                                        classStereotypes={reactiveClass.stereotypes}
                                    />
                                    <tr>
                                        <td colspan="2">
                                            <div
                                                class="flex size-full flex-col space-y-1.5"
                                            >
                                                {#if isEnum}
                                                    <EnumEntries
                                                        enumEntries={reactiveClass.enumEntries}
                                                        {inheritedEnumEntries}
                                                        cls={reactiveClass}
                                                    />
                                                {:else}
                                                    <Attributes
                                                        attributes={reactiveClass.attributes}
                                                        {inheritedAttributes}
                                                        {openPropertySHACLRulesDialog}
                                                    />
                                                    <Associations
                                                        associations={reactiveClass.associations}
                                                        {inheritedAssociations}
                                                        {openPropertySHACLRulesDialog}
                                                    />
                                                {/if}
                                            </div>
                                        </td>
                                    </tr>
                                </tbody>
                            </table>
                        </div>
                    </div>
                </div>
            </Pane>

            <Pane
                size={25}
                class="bg-window-background flex h-full flex-col space-y-1 rounded-xs border-none px-2 pb-2"
            >
                <Comment comment={reactiveClass.comment} />
            </Pane>
            <ShaclPropertySpecificDialog
                bind:showDialog={propertyShaclRulesDialog.showDialog}
                property={propertyShaclRulesDialog.property}
                classUuidOverride={propertyShaclRulesDialog.classUuidOverride}
            />
        {/if}
    </Splitpanes>
    {#if loadingClass || loadingContext}
        <div
            class="absolute inset-0 z-50 flex items-center justify-center bg-white/50"
        >
            <LoadingSpinner />
        </div>
    {/if}
</div>
