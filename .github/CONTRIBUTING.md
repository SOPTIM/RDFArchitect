# Contributing to RDFArchitect

Thank you for your interest in contributing to RDFArchitect.

## Before You Start

- Read the [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).
- For security issues, do not open public issues. Follow [SECURITY.md](SECURITY.md).
- For usage and support channels, see [SUPPORT.md](SUPPORT.md).

## Repository Structure

- `backend/`: Spring Boot backend (Java/Maven)
- `frontend/`: Svelte frontend (Node/npm)
- `.github/workflows/`: CI workflows for backend and frontend

## Development Setup

### Backend

```bash
cd backend
mvn clean install
mvn spring-boot:run
```

### Frontend

```bash
cd frontend
npm install
npm run dev
```

## Branching and Pull Requests

- Create focused branches from `main`.
- Keep changes scoped and reviewable.
- Open a pull request using `.github/pull_request_template.md`.
- Pull requests are merged via squash commits.
- Ensure your PR description explains:
  - What changed
  - Why it changed
  - How it was tested

## Required Checks Before Opening a PR

### Backend checks

```bash
cd backend
mvn -B test
mvn -B verify
```

### Frontend checks

```bash
cd frontend
npm run test
npm run lint
npm run build
```

CI runs these checks through GitHub Actions and must pass.

## Code Quality Expectations

- Follow existing code style and naming conventions.
- Add or update tests for behavior changes.
- Keep public behavior and API changes documented.
- Avoid unrelated refactors in feature/fix PRs.

## Licensing and Headers

- This repository is Apache-2.0 licensed.
- Java files are validated with license header checks in backend CI.
- Keep third-party license files current when dependencies change.

## Squash Commit Format

Squash commit titles should follow this format:

```text
<type>[optional scope][!]: <description> (#<pr>, RDFA-<id>, GH-<issue>)
```

Examples:

```text
feat(editor): add class filter (#123, RDFA-456, GH-78)
fix(api)!: change namespace validation rules (#124, RDFA-457, GH-79)
```

Notes:

- `type` should be `feat` or `fix` for product-facing changes whenever possible.
- `#<pr>`, `RDFA-<id>`, and `GH-<issue>` are optional in automation, but include all available IDs in squash commits.
- Keep the description short, action-oriented, and specific.

## Breaking Changes

Breaking changes are detected when either of these is present:

- `!` in the commit header (`feat!:` or `feat(scope)!:`)
- `BREAKING CHANGE:` in the commit body

Use one of these markers whenever the change is not backward compatible.

## Changelog Automation

The changelog workflow reads new commits and updates `CHANGELOG.md` automatically.

- On pushes to `main`, it appends entries to `## [Unreleased]`.
- On `vX.Y.Z` tags, it moves `Unreleased` into the matching release section with date and creates a fresh `Unreleased`.
- Entries are deduplicated by any known identifier (`commit`, `PR`, `GH issue`, `RDFA`) and by normalized description.
- Manual edits are preserved; if an entry already exists elsewhere (for example in a custom section), automation does not add it again.

Generated changelog entry format:

```text
GH-<issue>: Description ([#<pr>](...), RDFA-<id>, [<sha8>](...))
```

## Contributor Checklist

- Keep branch changes scoped to one topic.
- Ensure the squash commit title follows the required format.
- Try to use conventional commits in the feature branch to make squash commit writing easier.
- Mark breaking changes with `!` or `BREAKING CHANGE:`.
- Run required checks locally before opening/updating the PR.

## Review Process

- Maintainers may request changes before merge.
- PRs should be rebased/updated to resolve conflicts.
- Merges happen after approval and passing CI.
