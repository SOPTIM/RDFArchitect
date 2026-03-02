# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- [RDFA-404](https://tickets.soptim.de/browse/RDFA-404): Added documentation and issue templates ([GH-32](https://github.com/SOPTIM/RDFArchitect/pull/32))

### Changed

- Pin GitHub Action versions to full-length commit SHAs for better replicability and security ([GH-28](https://github.com/SOPTIM/RDFArchitect/pull/28), [GH-31](https://github.com/SOPTIM/RDFArchitect/pull/31))

### Fixed

- Ignore GitHub Actions bot in Renovate pull requests ([3d958240](https://github.com/SOPTIM/RDFArchitect/commit/3d958240))
- [RDFA-333](https://tickets.soptim.de/browse/RDFA-333): Added exception handling for failed property shape generation ([94082045](https://github.com/SOPTIM/RDFArchitect/commit/94082045), [GH-6](https://github.com/SOPTIM/RDFArchitect/pull/6))

## [0.14.0] - 2026-02-24

### Added

- Initial commit - Transferred repo to GitHub ([690cba17](https://github.com/SOPTIM/RDFArchitect/commit/690cba17))
- Add `.git-blame-ignore-revs` for cleaner blame history ([97b1a280](https://github.com/SOPTIM/RDFArchitect/commit/97b1a280))
- [RDFA-340](https://tickets.soptim.de/browse/RDFA-340): Added support for adding empty graphs ([ffb900d6](https://github.com/SOPTIM/RDFArchitect/commit/ffb900d6), [GH-5](https://github.com/SOPTIM/RDFArchitect/pull/5))

### Changed

- [RDFA-332](https://tickets.soptim.de/browse/RDFA-332): Set TTL as default format for SHACL export ([e72119b4](https://github.com/SOPTIM/RDFArchitect/commit/e72119b4), [GH-9](https://github.com/SOPTIM/RDFArchitect/pull/9))
- [RDFA-192](https://tickets.soptim.de/browse/RDFA-192): Navigation entries are now sorted alphabetically ([1f90af0f](https://github.com/SOPTIM/RDFArchitect/commit/1f90af0f), [GH-12](https://github.com/SOPTIM/RDFArchitect/pull/12))
- [RDFA-267](https://tickets.soptim.de/browse/RDFA-267): Updated GitHub Links in Help menu bar ([e301d52f](https://github.com/SOPTIM/RDFArchitect/commit/e301d52f), [GH-25](https://github.com/SOPTIM/RDFArchitect/pull/25))


### Fixed

- [RDFA-393](https://tickets.soptim.de/browse/RDFA-393): Enhance concurrency handling in `GraphRewindableTest` ([15172067](https://github.com/SOPTIM/RDFArchitect/commit/15172067), [GH-4](https://github.com/SOPTIM/RDFArchitect/pull/4))
- [RDFA-362](https://tickets.soptim.de/browse/RDFA-362): Fix CIM datatypes being miscategorized ([e65602c5](https://github.com/SOPTIM/RDFArchitect/commit/e65602c5), [GH-10](https://github.com/SOPTIM/RDFArchitect/pull/10))
- [RDFA-323](https://tickets.soptim.de/browse/RDFA-323): Fixed restore version in changelog ([7f5aa185](https://github.com/SOPTIM/RDFArchitect/commit/7f5aa185), [GH-7](https://github.com/SOPTIM/RDFArchitect/pull/7))
- [RDFA-389](https://tickets.soptim.de/browse/RDFA-389): Fixed Mermaid deadlock ([5fe351b3](https://github.com/SOPTIM/RDFArchitect/commit/5fe351b3), [GH-21](https://github.com/SOPTIM/RDFArchitect/pull/21))

