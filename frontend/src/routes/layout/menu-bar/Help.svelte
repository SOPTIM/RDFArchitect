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
    import {
        faCircleInfo,
        faCircleQuestion,
        faCommentDots,
    } from "@fortawesome/free-solid-svg-icons";

    import { Menubar } from "$lib/components/bitsui/menubar";
    import { editorState } from "$lib/sharedState.svelte.js";

    import { goto } from "$app/navigation";

    const externalResources = {
        help: "https://github.com/SOPTIM/rdfarchitect#readme",
        feedback: "https://github.com/SOPTIM/rdfarchitect/issues/new/choose",
    };

    function openExternalResource(key) {
        const target = externalResources[key];
        if (!target) return;
        if (typeof window !== "undefined") {
            window.open(target, "_blank", "noopener,noreferrer");
        }
    }

    function navigateHomepage() {
        editorState.reset();
        goto("/");
    }
</script>

<Menubar.Menu value="help">
    <Menubar.Trigger>Help</Menubar.Trigger>
    <Menubar.Content side="bottom" sideOffset={8}>
        <Menubar.Item.Button
            onSelect={() => openExternalResource("help")}
            faIcon={faCircleQuestion}
        >
            Help
        </Menubar.Item.Button>
        <Menubar.Item.Button
            onSelect={() => openExternalResource("feedback")}
            faIcon={faCommentDots}
        >
            Submit Feedback
        </Menubar.Item.Button>
        <Menubar.Item.Button onSelect={navigateHomepage} faIcon={faCircleInfo}>
            About
        </Menubar.Item.Button>
    </Menubar.Content>
</Menubar.Menu>
