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
    import { PUBLIC_BACKEND_URL } from "$lib/config/runtime";
    import { eventStack } from "$lib/eventhandling/closeEventManager.svelte.js";
    import { mapClassDtoToReactiveClass } from "$lib/models/reactive/mapper/map-dto-to-reactive-object.js";
    import {
        editorState,
        forceReloadTrigger,
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
    import Uuid from "./components/Uuid.svelte";
    import LoadingSpinner from "$lib/components/LoadingSpinner.svelte";

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
    };

    let isDatasetReadOnly = $state(false);

    let reactiveClass = $state();

    let loadingContext = $state(true);

    let loadingClass = $state(true);

    const propertyShaclRulesDialog = $state({
        showDialog: false,
        property: null,
    });

    let showDiscardSaveConfirmDialog = $state(false);

    let datasetOfClassToOpenNext = $state(null);
    let graphOfClassToOpenNext = $state(null);
    let classToOpenNext = $state(null);

    let isEnum = $derived(
        reactiveClass?.stereotypes.contains(enumerationStereotype),
    );

    onMount(async () => {
        isDatasetReadOnly = await isReadOnly(datasetName);
        await loadContext();
        await loadReactiveClass();
    });

    onMount(() => eventStack.addEvent(closeClassEditor));

    onDestroy(() => eventStack.removeEvent(closeClassEditor));

    $effect(async () => {
        editorState.selectedClassUUID.subscribe();
        loadingContext = true;
        loadingClass = true;

        isDatasetReadOnly = await isReadOnly(datasetName);
        await loadContext();
        await loadReactiveClass();
    });

    $effect(async () => {
        editorState.selectedPackageUUID.subscribe();
        isDatasetReadOnly = await isReadOnly(datasetName);
    });

    function closeClassEditor(
        { datasetName = null, graphUri = null, classUuid = null } = {
            datasetName: null,
            graphUri: null,
            classUuid: null,
        },
    ) {
        if (!showDiscardSaveConfirmDialog && reactiveClass?.isModified) {
            showDiscardSaveConfirmDialog = true;
            datasetOfClassToOpenNext = datasetName;
            graphOfClassToOpenNext = graphUri;
            classToOpenNext = classUuid;
            return;
        }
        editorState.selectedClassDataset.updateValue(datasetName);
        editorState.selectedClassGraph.updateValue(graphUri);
        editorState.selectedClassUUID.updateValue(classUuid);
    }

    async function loadContext() {
        [
            context.classes,
            context.packages,
            context.datatypes,
            context.stereotypes,
            context.namespaces,
        ] = await Promise.all([
            getClasses(datasetName, graphUri),
            getPackages(datasetName, graphUri),
            getDataTypes(datasetName, graphUri),
            getStereotypes(datasetName, graphUri),
            getNamespaces(datasetName),
        ]);
        loadingContext = false;
        editorState.selectedContext.trigger();
    }

    async function loadReactiveClass() {
        const classDto = await (
            await bec.getClassInfo(datasetName, graphUri, classUuid)
        ).json();
        reactiveClass = mapClassDtoToReactiveClass(classDto, context.classes);
        loadingClass = false;
        console.log({ reactiveClass });
    }

    function openPropertySHACLRulesDialog(property) {
        propertyShaclRulesDialog.property = property;
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
        // get Objects by identifier functions
        get getClassByUuid() {
            return function (uuid) {
                return context.classes.find(cls => cls.uuid === uuid);
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
    });
</script>

<div class="relative h-screen w-full">
    <Splitpanes
        theme="opencgmes-theme"
        horizontal
        class="bg-window-background h-screen"
    >
        {#if reactiveClass}
            <Pane
                size={75}
                class="bg-window-background z-2 size-full rounded-xs border-none"
            >
                <div class="flex size-full flex-col p-2">
                    <ClassEditorButtons
                        {reactiveClass}
                        bind:showDiscardSaveConfirmDialog
                        {datasetOfClassToOpenNext}
                        {graphOfClassToOpenNext}
                        {classToOpenNext}
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
                                    <Uuid uuid={reactiveClass.uuid} />
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
                                                        cls={reactiveClass}
                                                    />
                                                {:else}
                                                    <Attributes
                                                        attributes={reactiveClass.attributes}
                                                        {openPropertySHACLRulesDialog}
                                                    />
                                                    <Associations
                                                        associations={reactiveClass.associations}
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
