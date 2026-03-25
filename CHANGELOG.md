# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.15.0] - 2026-03-17

### Breaking Changes
- RDFA-318: Added SvelteFlow Rendering and Layouting ([20361482](https://github.com/SOPTIM/RDFArchitect/commit/20361482), [#27](https://github.com/SOPTIM/RDFArchitect/pull/27))

### Added

- RDFA-350: Export puts Ontology as the first resource ([830b5da7](https://github.com/SOPTIM/RDFArchitect/commit/830b5da7), [#42](https://github.com/SOPTIM/RDFArchitect/pull/42))
- RDFA-337: Clear reset for package editor ([d0cf78bd](https://github.com/SOPTIM/RDFArchitect/commit/d0cf78bd), [#11](https://github.com/SOPTIM/RDFArchitect/pull/11))
- RDFA-261: Manage Property prefixes ([2611293](https://github.com/SOPTIM/RDFArchitect/commit/2611293), [#8](https://github.com/SOPTIM/RDFArchitect/pull/8))
- RDFA-404: Added documentation and git tag based versioning ([19cd133](https://github.com/SOPTIM/RDFArchitect/commit/19cd133), [#32](https://github.com/SOPTIM/RDFArchitect/pull/32))

### Changed

- Pin GitHub Action versions to full-length commit SHAs for better replicability and security ([#28](https://github.com/SOPTIM/RDFArchitect/pull/28), [#31](https://github.com/SOPTIM/RDFArchitect/pull/31))

### Fixed

- Ignore GitHub Actions bot in Renovate pull requests ([3d958240](https://github.com/SOPTIM/RDFArchitect/commit/3d958240))
- RDFA-333: Added exception handling for failed property shape generation ([94082045](https://github.com/SOPTIM/RDFArchitect/commit/94082045), [#6](https://github.com/SOPTIM/RDFArchitect/pull/6))
- RDFA-281: Fixed SonarQube Code Quality issues ([298723a](https://github.com/SOPTIM/RDFArchitect/commit/298723a), [#35](https://github.com/SOPTIM/RDFArchitect/pull/35))

## [0.14.0] - 2026-02-24

### Added

- Initial commit - Transferred repo to GitHub ([690cba17](https://github.com/SOPTIM/RDFArchitect/commit/690cba17))
- Add `.git-blame-ignore-revs` for cleaner blame history ([97b1a280](https://github.com/SOPTIM/RDFArchitect/commit/97b1a280))
- RDFA-340: Added support for adding empty graphs ([ffb900d6](https://github.com/SOPTIM/RDFArchitect/commit/ffb900d6), [#5](https://github.com/SOPTIM/RDFArchitect/pull/5))

### Changed

- RDFA-332: Set TTL as default format for SHACL export ([e72119b4](https://github.com/SOPTIM/RDFArchitect/commit/e72119b4), [#9](https://github.com/SOPTIM/RDFArchitect/pull/9))
- RDFA-192: Navigation entries are now sorted alphabetically ([1f90af0f](https://github.com/SOPTIM/RDFArchitect/commit/1f90af0f), [#12](https://github.com/SOPTIM/RDFArchitect/pull/12))
- RDFA-267: Updated GitHub Links in Help menu bar ([e301d52f](https://github.com/SOPTIM/RDFArchitect/commit/e301d52f), [#25](https://github.com/SOPTIM/RDFArchitect/pull/25))


### Fixed

- RDFA-393: Enhance concurrency handling in `GraphRewindableTest` ([15172067](https://github.com/SOPTIM/RDFArchitect/commit/15172067), [#4](https://github.com/SOPTIM/RDFArchitect/pull/4))
- RDFA-362: Fix CIM datatypes being miscategorized ([e65602c5](https://github.com/SOPTIM/RDFArchitect/commit/e65602c5), [#10](https://github.com/SOPTIM/RDFArchitect/pull/10))
- RDFA-323: Fixed restore version in changelog ([7f5aa185](https://github.com/SOPTIM/RDFArchitect/commit/7f5aa185), [#7](https://github.com/SOPTIM/RDFArchitect/pull/7))
- RDFA-389: Fixed Mermaid deadlock ([5fe351b3](https://github.com/SOPTIM/RDFArchitect/commit/5fe351b3), [#21](https://github.com/SOPTIM/RDFArchitect/pull/21))
