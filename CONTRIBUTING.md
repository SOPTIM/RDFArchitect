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

## Commit Guidance

- Use clear, descriptive commit messages.
- Prefer small commits with a single purpose.
- Reference issue IDs when applicable.
- Use conventional commits format if possible.

## Review Process

- Maintainers may request changes before merge.
- PRs should be rebased/updated to resolve conflicts.
- Merges happen after approval and passing CI.
