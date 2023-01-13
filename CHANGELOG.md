## Unreleased

### Added

- All missing ios, watchos, tvos and macos simulator targets added
- Added `androidNativeX64` and `androidNativeX86` targets
- Added proper android release and debug variants instead of piggybacking on jvm artefact

### Changed

- Major gradle infra rework
- Enabled `explicitPublicApi()`

### Removed

- Remove deprecated `wasm32` target

## [0.5.5] - 2020-08-16

- update to Kotlin 1.4.0
- added platforms (androidNativeArm32, androidNativeArm64, iosArm32, linuxArm64, linuxX64,
  mingwX86, tvosArm64, tvosX64, watchosArm32, watchosArm64, watchosX86)
- remove spek & atrium deps and use plain kotlin tests & assertions. Tests run for all platforms now.

## [0.5.2] - 2020-07-03

- publish all available platforms to maven
- add CI/CD through github actions

## [0.5.1] - 2020-06-11

- update lib dependency to api import, so core lib is included in redux-kotlin-threadsafe

## [0.5.0] - 2020-06-11

- kotlin 1.3.72
- createThreadSafeStore fun added for thread synchronized access
- createEnsureSameThreadStore to provide existing same-thread-enforcement

## [0.4.0] - 2020-03-23

- kotlin 1.3.70

## [0.3.2] - 2020-02-22

- issue #34 - incorrect same thread enforcement behavior fixed

## [0.3.1] - 2019-12-16

### Changed

- update same thread enforcement message to not be getState only

## [0.3.0] - 2019-12-16

### Added

- thread enforcement

## [0.2.9] - 2019-11-23

### Changed

- update Kotlin to 1.3.60
