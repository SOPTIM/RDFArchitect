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
    import { faCopyright } from "@fortawesome/free-regular-svg-icons";
    import {
        faPenToSquare,
        faCodeBranch,
        faCircleInfo,
        faShieldHalved,
        faServer,
        faTag,
    } from "@fortawesome/free-solid-svg-icons";
    import { Fa } from "svelte-fa";

    import { env } from "$env/dynamic/public";

    import InteractiveNetwork from "$lib/components/InteractiveNetwork.svelte";

    import { goto } from "$app/navigation";

    const isLocal = !env.PUBLIC_COMMIT_REF || !env.PUBLIC_COMMIT_REF.trim();
    const version = isLocal
        ? `v${env.PUBLIC_APP_VERSION}-SNAPSHOT`
        : `v${env.PUBLIC_APP_VERSION}`;
    const cluster = isLocal
        ? "local"
        : (() => {
              switch (env.PUBLIC_COMMIT_REF.trim()) {
                  case "develop":
                      return "dev";
                  case "main":
                      return "prod";
                  default:
                      return env.PUBLIC_COMMIT_REF.trim();
              }
          })();

    const repoCommitBaseUrl =
        env.PUBLIC_REPOSITORY_URL && env.PUBLIC_REPOSITORY_URL.trim()
            ? env.PUBLIC_REPOSITORY_URL.trim().replace(/\/$/, "") + "/-/commit/"
            : "";

    const leftItems = $derived([
        ...(env.PUBLIC_APP_VERSION ? [{ icon: faTag, label: version }] : []),
        { icon: faServer, label: cluster },
        ...(env.PUBLIC_COMMIT_SHA
            ? [
                  {
                      icon: faCodeBranch,
                      label: env.PUBLIC_COMMIT_SHA,
                      href: repoCommitBaseUrl + env.PUBLIC_COMMIT_SHA,
                  },
              ]
            : []),
    ]);
</script>

<section class="relative h-full w-full overflow-auto pb-12">
    <!-- Hero -->
    <div class="relative overflow-hidden">
        <!-- Layered blue gradient background -->
        <div
            class="absolute inset-0 -z-10"
            style="background-image:
                radial-gradient(600px 400px at 15% 5%, var(--color-blue) 0%, transparent 60%),
                radial-gradient(700px 500px at 85% -10%, var(--color-soptim-blau) 0%, transparent 55%),
                linear-gradient(180deg, var(--color-soptim-blau) 0%, var(--color-blue) 80%, var(--color-soptim-blau) 100%)
            ;"
        ></div>

        <!-- Subtle interactive network -->
        <div class="pointer-events-none absolute inset-0 -z-0 opacity-70">
            <InteractiveNetwork
                nodeCount={80}
                lineDistance={150}
                repulsionRadius={110}
                speed={0.22}
                color="255,255,255"
                opacity={0.28}
            />
        </div>

        <div
            class="text-button-default-text relative z-10 mx-auto max-w-5xl px-6 py-20"
        >
            <h1 class="text-4xl font-extrabold tracking-tight">
                RDF Architect
            </h1>
            <p class="mt-3 max-w-2xl text-base opacity-90">
                A clean, fast editor to model, explore and evolve RDF schemas.
            </p>
            <div class="mt-8">
                <button
                    id="hero-open-editor"
                    class="bg-window-background text-default-text hover:bg-button-hover-background hover:text-button-hover-text inline-flex h-12 items-center gap-2 rounded px-6 text-base font-semibold shadow transition-colors hover:cursor-pointer"
                    onclick={() => goto("/mainpage")}
                >
                    <Fa icon={faPenToSquare} />
                    Open Editor
                </button>
            </div>
        </div>
    </div>

    <div class="mx-auto max-w-5xl space-y-12 px-6 py-12">
        <!-- Simple feature highlights -->
        <div class="grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
            <div
                class="bg-window-background border-border text-default-text rounded-lg border p-5 shadow-sm"
            >
                <h3 class="text-base font-semibold">Model Schemas</h3>
                <p class="mt-2 text-sm opacity-80">
                    Create and edit classes, attributes and associations with an
                    intuitive UI.
                </p>
            </div>
            <div
                class="bg-window-background border-border text-default-text rounded-lg border p-5 shadow-sm"
            >
                <h3 class="text-base font-semibold">Track Changes</h3>
                <p class="mt-2 text-sm opacity-80">
                    Review modifications and iterate confidently with undo/redo
                    support.
                </p>
            </div>
            <div
                class="bg-window-background border-border text-default-text rounded-lg border p-5 shadow-sm"
            >
                <h3 class="text-base font-semibold">SHACL</h3>
                <p class="mt-2 text-sm opacity-80">
                    Generate SHACL shapes and display them contextually.
                </p>
            </div>
            <div
                class="bg-window-background border-border text-default-text rounded-lg border p-5 shadow-sm"
            >
                <h3 class="text-base font-semibold">Share</h3>
                <p class="mt-2 text-sm opacity-80">
                    Share snapshots to reproduce views.
                </p>
            </div>
        </div>

        <!-- Tips -->
        <div>
            <h2
                class="text-default-text flex items-center gap-2 text-xl font-semibold"
            >
                <Fa icon={faCircleInfo} />
                Tips
            </h2>
            <div class="text-default-text/90 mt-3 space-y-2 text-sm">
                <p>
                    • Use the search bar at the top to quickly find classes,
                    attributes or packages across datasets.
                </p>
                <p>
                    • Keyboard shortcuts: Undo (Ctrl+Z) and Redo (Ctrl+Y) work
                    when editing.
                </p>
                <p>
                    • Share current state via “Share Snapshot” in the menu to
                    reproduce a view.
                </p>
                <p>
                    • Import or export graphs from the menu to move data between
                    environments.
                </p>
            </div>
        </div>

        <!-- Security & Data -->
        <div>
            <h2
                class="text-default-text flex items-center gap-2 text-xl font-semibold"
            >
                <Fa icon={faShieldHalved} />
                Security & Data
            </h2>
            <div
                class="bg-window-background border-border text-default-text mt-3 rounded border p-4 text-sm shadow-sm"
            >
                <ul class="list-disc space-y-2 pl-5">
                    <li>
                        <strong>Local development</strong>
                        <ul class="mt-1 list-disc space-y-1 pl-5">
                            <li>
                                Editor data is kept only in the local backend
                                during runtime. Restarting the backend clears
                                the data.
                            </li>
                            <li>
                                When you share a snapshot, the snapshot data is
                                uploaded to Fuseki and persists there.
                            </li>
                        </ul>
                    </li>
                    <li>
                        <strong>Cluster (internal)</strong>
                        <ul class="mt-1 list-disc space-y-1 pl-5">
                            <li>
                                The cluster is reachable only on the internal
                                network; it is not publicly accessible.
                            </li>
                            <li>
                                Data is session-specific and isolated; other
                                users cannot view your session’s data.
                            </li>
                            <li>
                                Session data is ephemeral and is removed when
                                the cluster restarts (e.g., on a new push to the
                                repository).
                            </li>
                            <li>
                                When you share a snapshot, the snapshot data is
                                uploaded to Fuseki and remains there. Snapshots
                                cannot currently be deleted.
                            </li>
                        </ul>
                    </li>
                </ul>
            </div>
        </div>
    </div>

    <!-- Fixed footer at viewport bottom -->
    <footer
        class="border-border bg-window-background text-default-text supports-[backdrop-filter]:bg-window-background fixed right-0 bottom-0 left-0 z-40 border-t backdrop-blur"
    >
        <div
            class="flex w-full flex-row flex-wrap items-center justify-between gap-2 px-3 py-2"
        >
            <div class="flex items-center gap-2 text-xs sm:text-sm">
                {#each leftItems as item, i}
                    {#if i > 0}
                        <span class="text-default-text">•</span>
                    {/if}
                    <span class="inline-flex items-center gap-1.5">
                        <Fa icon={item.icon} class="opacity-80" />
                        {#if item.href}
                            <a
                                href={item.href}
                                target="_blank"
                                rel="noopener noreferrer"
                                class="hover:underline"
                                title="View commit"
                            >
                                {item.label}
                            </a>
                        {:else}
                            <span>{item.label}</span>
                        {/if}
                    </span>
                {/each}
            </div>
            <div class="text-xs sm:text-sm">
                Copyright <Fa icon={faCopyright} class="inline" /> 2024-2026 SOPTIM
                AG
            </div>
        </div>
    </footer>
</section>
