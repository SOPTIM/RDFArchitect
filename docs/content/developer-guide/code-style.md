---
title: Code Style and Quality Gates
sidebar_position: 7
---

# Code Style and Quality Gates

## Backend

| Tool                  | Purpose                                                     | Run                                  |
| --------------------- | ----------------------------------------------------------- | ------------------------------------ |
| **Spotless**          | google-java-format (AOSP), import order, trailing whitespace| `mvn -B spotless:apply`              |
| **Checkstyle**        | Style rules beyond formatting                               | `mvn -B -Plint -DskipTests verify`   |
| **SpotBugs**          | Bug pattern detection                                       | `mvn -B -Plint -DskipTests verify`   |
| **Mycila license-maven-plugin** | Apache 2.0 header on every Java file              | `mvn -B -Plint -DskipTests verify`   |
| **codehaus license-maven-plugin** | Third-party license aggregation                 | `mvn org.codehaus.mojo:license-maven-plugin:add-third-party` |

The `lint` Maven profile runs everything in one go without tests.

## Frontend

| Tool                          | Purpose                                                                  |
| ----------------------------- | ------------------------------------------------------------------------ |
| **Prettier**                  | Formatting (with `prettier-plugin-svelte` and `prettier-plugin-tailwindcss`) |
| **ESLint** (custom config)    | Lint rules, including project-specific custom rules                      |
| **Custom ESLint rules**       | Copyright header, Svelte file structure, Svelte script order             |
| **`licenses-third-party.js`** | Third-party license aggregation                                          |

Both `npm run lint` and `npm run format` run all three together.

## License header

Every source file (Java, JavaScript, TypeScript, Svelte) must carry the Apache 2.0 header. The auto-fixers add it for you. CI fails if it is missing.

## Renovate

The repository uses Renovate for dependency updates. CI is set up so that Renovate's PRs auto-regenerate `LICENSES-THIRD-PARTY.md` if a dependency change requires it. Human contributors do not need to do this manually — but if you change `pom.xml` or `package.json`, you may need to run the appropriate licenses task locally and commit the result.
