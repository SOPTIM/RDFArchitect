# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.1.0] - 2026-06-02

### Added

- RDFA-513: Added User Settings Dialog and Settings ([7a914713](https://github.com/SOPTIM/RDFArchitect/commit/7a914713), [#118](https://github.com/SOPTIM/RDFArchitect/pull/118))
- RDFA-381: Copy paste of classes ([c00558c9](https://github.com/SOPTIM/RDFArchitect/commit/c00558c9), [#93](https://github.com/SOPTIM/RDFArchitect/pull/93))
- Fixes for run with docker compose ([dcf0bc47](https://github.com/SOPTIM/RDFArchitect/commit/dcf0bc47), [#116](https://github.com/SOPTIM/RDFArchitect/pull/116))
- RDFA-243: Add toast notifications for user feedback across app ([0c3fb043](https://github.com/SOPTIM/RDFArchitect/commit/0c3fb043), [#124](https://github.com/SOPTIM/RDFArchitect/pull/124))
- RDFA-113: Automatic extension of classes ([4a2676fb](https://github.com/SOPTIM/RDFArchitect/commit/4a2676fb), [#122](https://github.com/SOPTIM/RDFArchitect/pull/122))
- RDFA-364: Add custom diagrams ([70641ec3](https://github.com/SOPTIM/RDFArchitect/commit/70641ec3), [#71](https://github.com/SOPTIM/RDFArchitect/pull/71))
- Make Enter key reliably trigger primary action in all dialogs ([ef3a9e83](https://github.com/SOPTIM/RDFArchitect/commit/ef3a9e83), [#121](https://github.com/SOPTIM/RDFArchitect/pull/121))
- RDFA-437: Use ENTSO-E application-profiles-library as submodule ([9f1eb336](https://github.com/SOPTIM/RDFArchitect/commit/9f1eb336), [#109](https://github.com/SOPTIM/RDFArchitect/pull/109))
- RDFA-419: Add workflow to automate changelog updates ([5bfdb56b](https://github.com/SOPTIM/RDFArchitect/commit/5bfdb56b), [#112](https://github.com/SOPTIM/RDFArchitect/pull/112))
- RDFA-504: Added config to set default stereotypes and namspaces ([78305671](https://github.com/SOPTIM/RDFArchitect/commit/78305671), [#106](https://github.com/SOPTIM/RDFArchitect/pull/106))
- RDFA-321: Support RDF blank nodes in attribute editor for fixed and default values ([839bd5e1](https://github.com/SOPTIM/RDFArchitect/commit/839bd5e1), [#22](https://github.com/SOPTIM/RDFArchitect/pull/22))
- RDFA-136: Show references on delete and allow selective deleting ([ef1d6ada](https://github.com/SOPTIM/RDFArchitect/commit/ef1d6ada), [#82](https://github.com/SOPTIM/RDFArchitect/pull/82))
- RDFA-434: Z-index for svelteflow Diagram ([b4bf26d4](https://github.com/SOPTIM/RDFArchitect/commit/b4bf26d4), [#83](https://github.com/SOPTIM/RDFArchitect/pull/83))

### Changed

- RDFA-410: Add Developer Certificate of Origin ([8ddfd6f2](https://github.com/SOPTIM/RDFArchitect/commit/8ddfd6f2), [#108](https://github.com/SOPTIM/RDFArchitect/pull/108))
- RDFA-501: Enhance documentation ([ef7203ff](https://github.com/SOPTIM/RDFArchitect/commit/ef7203ff), [#101](https://github.com/SOPTIM/RDFArchitect/pull/101))
- RDFA-439: Remove uuids from frontend ([770d11a6](https://github.com/SOPTIM/RDFArchitect/commit/770d11a6), [#84](https://github.com/SOPTIM/RDFArchitect/pull/84))
- Add bachelors thesis to repo ([6dd16b23](https://github.com/SOPTIM/RDFArchitect/commit/6dd16b23), [#94](https://github.com/SOPTIM/RDFArchitect/pull/94))
- Fix deploy workflow ([e15569aa](https://github.com/SOPTIM/RDFArchitect/commit/e15569aa), [#96](https://github.com/SOPTIM/RDFArchitect/pull/96))
- RDFA-497: Add documentation website ([c23ee085](https://github.com/SOPTIM/RDFArchitect/commit/c23ee085), [#95](https://github.com/SOPTIM/RDFArchitect/pull/95))
- Add GitHub PR validation workflows ([0d900d61](https://github.com/SOPTIM/RDFArchitect/commit/0d900d61), [#86](https://github.com/SOPTIM/RDFArchitect/pull/86))

### Fixed

- RDFA-576: Corrected the multiplicity rendering order ([218dea2c](https://github.com/SOPTIM/RDFArchitect/commit/218dea2c), [#131](https://github.com/SOPTIM/RDFArchitect/pull/131))
- RDFA-496: Fixed SearchableSelect valid input behaviour and state handling ([64d0ea94](https://github.com/SOPTIM/RDFArchitect/commit/64d0ea94), [#117](https://github.com/SOPTIM/RDFArchitect/pull/117))
- Remove ref specification in checkout step ([b729f06b](https://github.com/SOPTIM/RDFArchitect/commit/b729f06b), [#127](https://github.com/SOPTIM/RDFArchitect/pull/127))
- RDFA-529: Class context menu actions ([6cbdd1ef](https://github.com/SOPTIM/RDFArchitect/commit/6cbdd1ef), [#114](https://github.com/SOPTIM/RDFArchitect/pull/114))
- RDFA-507: Fixed that self associations double on class change save ([d6d1c07d](https://github.com/SOPTIM/RDFArchitect/commit/d6d1c07d), [#115](https://github.com/SOPTIM/RDFArchitect/pull/115))
- Update empty state text for schema import prompt ([873bda7e](https://github.com/SOPTIM/RDFArchitect/commit/873bda7e), [#110](https://github.com/SOPTIM/RDFArchitect/pull/110))
- RDFA-450: Render associations on the same class ([2e5b1719](https://github.com/SOPTIM/RDFArchitect/commit/2e5b1719), [#104](https://github.com/SOPTIM/RDFArchitect/pull/104))
- RDFA-502: Add custom shacl to graph context ([54c29adb](https://github.com/SOPTIM/RDFArchitect/commit/54c29adb), [#105](https://github.com/SOPTIM/RDFArchitect/pull/105))
- RDFA-481: Support packages without Package_ prefix ([e3740973](https://github.com/SOPTIM/RDFArchitect/commit/e3740973), [#78](https://github.com/SOPTIM/RDFArchitect/pull/78))
- RDFA-449: Association IRI violation checks ([353d8e42](https://github.com/SOPTIM/RDFArchitect/commit/353d8e42), [#75](https://github.com/SOPTIM/RDFArchitect/pull/75))
- Diagram reload UX and removed deprecated file database backend ([87852733](https://github.com/SOPTIM/RDFArchitect/commit/87852733), [#102](https://github.com/SOPTIM/RDFArchitect/pull/102))
- RDFA-96: Classes are now opened in the class editor on creation ([ad5e4450](https://github.com/SOPTIM/RDFArchitect/commit/ad5e4450), [#92](https://github.com/SOPTIM/RDFArchitect/pull/92))

## [1.0.0] - 2026-04-24

### Added

- RDFA-459: Added Maven-based backend linting ([10107dc2](https://github.com/SOPTIM/RDFArchitect/commit/10107dc2), [#67](https://github.com/SOPTIM/RDFArchitect/pull/67))

### Fixed

- Handle whitespace normalization for RDF comments in compare ([d4c5192e](https://github.com/SOPTIM/RDFArchitect/commit/d4c5192e), [#40](https://github.com/SOPTIM/RDFArchitect/pull/40))

## [0.16.0] - 2026-04-16

### Added

- RDFA-424: Multiple improvements to class editor: added close button, fixed file upload handling, improved import error visibility ([5e19b215](https://github.com/SOPTIM/RDFArchitect/commit/5e19b215), [#48](https://github.com/SOPTIM/RDFArchitect/pull/48))
- RDFA-408: Schema Migration ([f7d3edcf](https://github.com/SOPTIM/RDFArchitect/commit/f7d3edcf), [#57](https://github.com/SOPTIM/RDFArchitect/pull/57))
- RDFA-403: New closing buttons for dialogs ([b6009866](https://github.com/SOPTIM/RDFArchitect/commit/b6009866), [#55](https://github.com/SOPTIM/RDFArchitect/pull/55))
- RDFA-447: Enhanced Class Editor loading animation ([79436945](https://github.com/SOPTIM/RDFArchitect/commit/79436945), [#60](https://github.com/SOPTIM/RDFArchitect/pull/60))
- RDFA-343: Implement inline diff functionality for change comparison ([5c17f24e](https://github.com/SOPTIM/RDFArchitect/commit/5c17f24e), [#34](https://github.com/SOPTIM/RDFArchitect/pull/34))
- RDFA-305: Add class focus and diagram context menus ([af150a5a](https://github.com/SOPTIM/RDFArchitect/commit/af150a5a), [#36](https://github.com/SOPTIM/RDFArchitect/pull/36))

### Fixed

- RDFA-366: Refactor shared dialogs to bits-ui modal primitives ([00463562](https://github.com/SOPTIM/RDFArchitect/commit/00463562), [#56](https://github.com/SOPTIM/RDFArchitect/pull/56))
- RDFA-473: Fixed a number of issues ([805d29d3](https://github.com/SOPTIM/RDFArchitect/commit/805d29d3), [#77](https://github.com/SOPTIM/RDFArchitect/pull/77))
- RDFA-445: Added violation checks to ontology dialog ([93c59b21](https://github.com/SOPTIM/RDFArchitect/commit/93c59b21), [#68](https://github.com/SOPTIM/RDFArchitect/pull/68))
- RDFA-370: Added checks for class and attribute labels to have a unique IRI ([489a53b6](https://github.com/SOPTIM/RDFArchitect/commit/489a53b6), [#66](https://github.com/SOPTIM/RDFArchitect/pull/66))
- RDFA-433: Fixed faulty navigation API calls ([04858dd6](https://github.com/SOPTIM/RDFArchitect/commit/04858dd6), [#64](https://github.com/SOPTIM/RDFArchitect/pull/64))
- RDFA-371: Added unsaved changes adoption when saving a class property ([b1efffe2](https://github.com/SOPTIM/RDFArchitect/commit/b1efffe2), [#65](https://github.com/SOPTIM/RDFArchitect/pull/65))
- Fixed loading SPARQL templates from classpath in packaged backend ([9f6938d2](https://github.com/SOPTIM/RDFArchitect/commit/9f6938d2), [#73](https://github.com/SOPTIM/RDFArchitect/pull/73))
- Reset new item save state in class editor dialogs ([cbc2c912](https://github.com/SOPTIM/RDFArchitect/commit/cbc2c912), [#69](https://github.com/SOPTIM/RDFArchitect/pull/69))

## [0.15.1] - 2026-03-25

### Fixed

- RDFA-438: Use UUIDs for every resource ([#50](https://github.com/SOPTIM/RDFArchitect/pull/50))

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
