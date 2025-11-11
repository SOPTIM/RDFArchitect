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
    import { faRotateLeft, faTrash } from "@fortawesome/free-solid-svg-icons";

    import FaIconButton from "$lib/components/FaIconButton.svelte";

    const { obj, revertValue = null, class: cls } = $props();
</script>

<!--
This component relies objects to implement the following:
    - a value property that holds the current value
    - a isModified property that indicates if there are unsaved changes
    - a reset() method that reverts the value to a previous state
-->

<div class={cls ? cls : "flex w-fit space-x-1"}>
    <!--Clear data button-->
    <FaIconButton
        disabled={obj.value === null ||
            obj.value === undefined ||
            obj.value === ""}
        callOnClick={() => (obj.value = revertValue)}
        icon={faTrash}
    />

    <!--Reset data button-->
    <FaIconButton
        disabled={!obj.isModified}
        callOnClick={() => obj.reset()}
        icon={faRotateLeft}
    />
</div>
