<!--
  -    Copyright (c) 2024-2026 SOPTIM AG
  -
  -    Licensed under the Apache License, Version 2.0 (the "License");
  -    you may not use this file except in compliance with the License.
  -    You may obtain a copy of the License at
  -
  -    http://www.apache.org/licenses/LICENSE-2.0
  -
  -    Unless required by applicable law or agreed to in writing, software
  -    distributed under the License is distributed on an "AS IS" BASIS,
  -    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  -    See the License for the specific language governing permissions and
  -    limitations under the License.
  -->

<script>
    import PropertyMappingStep from "./PropertyMappingStep.svelte";

    const props = $props();
</script>

<PropertyMappingStep
    {...props}
    kind="enumEntries"
    resourceType="Enum Entry"
    addedTitle="Added Enum Entries"
    emptyStateDescription="There are no enum entry changes to review in this migration."
>
    <p>
        This step lets you review potential enum entry renames for all classes.
        Link a deleted entry to more than one target to split it, or link two
        deleted entries to the same target to merge them.
        <br />
        Please verify that the detected mappings are correct, or adjust them as necessary.
    </p>
</PropertyMappingStep>
