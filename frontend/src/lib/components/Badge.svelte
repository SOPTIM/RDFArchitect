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
    let { text, variant = "default" } = $props();

    const backgrounds = {
        default: "bg-nav-badge-background text-nav-badge-text",
        external: "bg-nav-external-badge-background text-nav-badge-text",
        muted: "bg-border-strong text-default-text",
        readonly: "bg-border-strong text-default-text",
    };
</script>

<span
    class="ml-auto shrink-0 rounded-full px-2 py-[0.1rem] text-[0.68rem] tracking-[0.04em] whitespace-nowrap uppercase {backgrounds[
        variant
    ] ?? backgrounds.default}"
>
    {text}
</span>
