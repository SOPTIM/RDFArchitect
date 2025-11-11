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
    let { title = "RDFArchitect", class: className = "", onclick } = $props();
</script>

<button
    type="button"
    class={"brand-logo " + className}
    aria-label={title}
    {onclick}
>
    <span class="brand-wordmark">
        <span class="brand-wordmark__accent">RDFA</span>
        <span class="brand-wordmark__label">rchitect</span>
    </span>
</button>

<style>
    .brand-logo {
        display: inline-flex;
        align-items: center;
        gap: 0.7rem;
        padding: 2rem 0.65rem;
        border: 1px solid transparent;
        border-radius: 0.45rem;
        background: transparent;
        color: inherit;
        font: inherit;
        cursor: pointer;
        transition: background-color 0.18s ease;
    }

    .brand-logo:focus-visible {
        outline: 2px solid var(--color-button-default-background);
        outline-offset: 2px;
        background: var(--color-input-default-background);
    }

    .brand-wordmark {
        display: inline-flex;
        align-items: baseline;
        gap: 0;
        font-size: 1.28rem;
        letter-spacing: 0.016em;
        line-height: 1;
    }

    .brand-wordmark__accent {
        font-weight: 700;
        color: var(--color-button-default-background);
        letter-spacing: 0.08em;
    }

    .brand-wordmark__label {
        font-weight: 600;
        color: var(--color-default-text);
        margin-left: -0.03rem;
    }

    @media (max-width: 620px) {
        .brand-wordmark {
            font-size: 1.12rem;
        }
    }
</style>
