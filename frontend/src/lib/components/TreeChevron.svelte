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
    import { faCaretRight } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";

    let {
        /**
         * Whether what it opens is open. The caret is the one the navigation tree uses, turned
         * rather than swapped for the one pointing down, so that it moves where it is pressed.
         */
        expanded = false,
        /**
         * Whether it stands for a row that opens nothing. The room is kept all the same, which is
         * what lines such a row up with the rows around it.
         */
        placeholder = false,
    } = $props();
</script>

<!-- Turned rather than scaled on hover: a caret this small is rasterised once and stretched by
     the compositor, which leaves it soft. The colour says the same thing and stays sharp. -->
<span
    class="flex w-3 shrink-0 items-center justify-center transition duration-200
        {placeholder ? 'invisible' : 'text-text-subtle hover:text-blue'}
        {expanded ? 'rotate-90' : ''}"
    aria-hidden="true"
>
    <Fa icon={faCaretRight} />
</span>
