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
    let { size = 40, stroke = 4, ariaLabel = "Loading" } = $props();
</script>

<div
    class="mm-spinner"
    style={`width:${size}px; height:${size}px; border-width:${stroke}px;`}
    aria-label={ariaLabel}
></div>

<style>
    .mm-spinner {
        border-radius: 50%;
        border-style: solid;
        border-color: var(--color-border);
        border-top-color: var(--color-button-default-background);
        animation: mm-spin 0.8s linear infinite;
        box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
    }

    @keyframes mm-spin {
        to {
            transform: rotate(360deg);
        }
    }
</style>
