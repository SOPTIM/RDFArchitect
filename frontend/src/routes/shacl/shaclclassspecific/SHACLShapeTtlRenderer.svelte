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
    import TurtleEditor from "$lib/monaco/TurtleEditor.svelte";

    import SHACLNodeShapeEditor from "./editors/SHACLNodeShapeEditor.svelte";
    import ShaclPropertyShapeWrapperListEditor from "./editors/SHACLPropertyShapeWrapperListEditor.svelte";

    let {
        namespaces,
        nodeShapesList,
        propertyShapesWrapperList,
        derivedPropertyShapesWrapperList,
    } = $props();

    let showNamespaces = $state(false);
    let showNodeShapes = $state(true);
    let showPropertyShapes = $state(true);
    let showDerivedPropertyShapes = $state(true);

    const hasAnything = $derived(
        (nodeShapesList?.length ?? 0) > 0 ||
            (propertyShapesWrapperList?.length ?? 0) > 0 ||
            (derivedPropertyShapesWrapperList?.length ?? 0) > 0,
    );
</script>

<!--
  @component
  The shapes that target one class, as the Turtle they were written in.

  Read-only. Shapes reach this view merged from every enabled document, but the endpoints that used
  to write them back always wrote to the graph's *default* document — so editing a rule that came
  from an imported file left the original in place and added a second, contradicting copy, and the
  edit never reached the document's text at all. Editing constraints is the workbench's job; this
  view answers "what is enforced on this class".
-->

<div class="flex h-fit flex-col gap-1">
    {#if !hasAnything}
        <p class="text-text-subtle py-2 text-sm italic">
            No constraints target this class.
        </p>
    {/if}

    {#if nodeShapesList && nodeShapesList.length > 0}
        <div>
            <button
                class="text-default-text w-fit text-sm font-semibold hover:cursor-pointer hover:underline"
                onclick={() => (showNodeShapes = !showNodeShapes)}
            >
                Class rules ({nodeShapesList.length})
            </button>
            {#if showNodeShapes}
                <SHACLNodeShapeEditor {nodeShapesList} />
            {/if}
        </div>
    {/if}

    {#if propertyShapesWrapperList && propertyShapesWrapperList.length > 0}
        <div>
            <button
                class="text-default-text w-fit text-sm font-semibold hover:cursor-pointer hover:underline"
                onclick={() => (showPropertyShapes = !showPropertyShapes)}
            >
                Property rules ({propertyShapesWrapperList.length})
            </button>
            {#if showPropertyShapes}
                <ShaclPropertyShapeWrapperListEditor
                    {propertyShapesWrapperList}
                />
            {/if}
        </div>
    {/if}

    {#if derivedPropertyShapesWrapperList && derivedPropertyShapesWrapperList.length > 0}
        <div>
            <button
                class="text-default-text w-fit text-sm font-semibold hover:cursor-pointer hover:underline"
                onclick={() =>
                    (showDerivedPropertyShapes = !showDerivedPropertyShapes)}
                title="Rules the class inherits rather than declares"
            >
                Inherited property rules ({derivedPropertyShapesWrapperList.length})
            </button>
            {#if showDerivedPropertyShapes}
                <ShaclPropertyShapeWrapperListEditor
                    propertyShapesWrapperList={derivedPropertyShapesWrapperList}
                />
            {/if}
        </div>
    {/if}

    {#if namespaces && namespaces.length > 0}
        <div>
            <button
                class="text-text-subtle w-fit text-xs hover:cursor-pointer hover:underline"
                onclick={() => (showNamespaces = !showNamespaces)}
            >
                Prefixes
            </button>
            {#if showNamespaces}
                <TurtleEditor autoGrow value={namespaces} readOnly={true} />
            {/if}
        </div>
    {/if}
</div>
