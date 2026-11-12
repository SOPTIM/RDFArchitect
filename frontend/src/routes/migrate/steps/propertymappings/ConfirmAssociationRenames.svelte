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

<PropertyMappingStep {...props} kind="associations" resourceType="Association">
    <p>
        This step lets you review potential association renames and moves for
        all classes. A candidate from elsewhere in the inheritance chain is
        labeled with its class; link a deleted association to more than one
        target to split it, or link two deleted associations to the same target
        to merge them.
        <br />
        Please verify that the detected mappings are correct, or adjust them as necessary.
        <br />
        Associations that cannot be instantiated (AssociationUsed = "No") are not
        listed: they hold no instance data, so there is nothing to map.
    </p>
</PropertyMappingStep>
