# ADR-0025 - Dependency injection in the Android app

Status: accepted (2026-11-19), revised (2026-11-20): Hilt is the final standard

## Context

`AppContainer` is one object, built once in `HopaApplication`, passed whole into
every ViewModel factory and every route. It is 413 lines and exposes settings,
conversations, memory, skills, runs, the backend link, the bridge, the hidden
display, model ports and the database. Nothing tells a reader which part a
screen actually uses, and a test cannot build a ViewModel without the whole app.

Modern Android commonly solves this with Hilt. The cost here is concrete: a new
Gradle plugin and KSP, `@HiltAndroidApp`, `@AndroidEntryPoint`, bindings for
every port, `hiltViewModel()` in navigation, and a rewrite of every existing
factory. That is a separate project, not a line item in a packaging refactor.

## Decision

1. Split the container by concern first, without a framework: a small number of
   narrow containers (core data, link/pairing, surfaces, model) assembled by one
   composition root.
2. Each ViewModel factory takes only the narrow container it uses, so a
   signature names its dependencies.
3. Do not adopt Hilt now. Reconsider if the app gains a second Android entry
   point with a different graph, or if the manual wiring starts to cost more
   than the plugin (measured, not assumed).

## Consequences

- Dependencies become visible at the factory boundary without code generation.
- JVM tests keep working: a narrow container can be faked without Android.
- `AppContainer` stays as the composition root, but stops being the thing every
  screen receives.
- If Hilt is adopted later, the narrow containers map to modules and bindings;
  the ViewModels do not need to change shape.
- First slices: `ModelSettings` takes `SettingsRepository` and `ModelFactory`,
  `PreferencesSettings` takes `SettingsRepository`, and every feature ViewModel
  takes its own dependency interface (`ChatDependencies`, `SettingsDependencies`,
  `PairingDependencies`, `SurfaceDependencies`, …). `AppContainer` is the
  composition root that implements all of them.
- This decision does not block the type-safe navigation or the Gradle
  modularisation work.

## Revision (2026-11-20)

The narrow containers were the right first step: they made dependencies
explicit and kept JVM tests working. With the app now split into feature
modules, the final standard is Hilt.

- `AppContainer` becomes a `@Singleton` built by Hilt, not passed by hand.
- Each dependency interface is bound to the container with `@Binds`.
- ViewModels become `@HiltViewModel` and routes use `hiltViewModel()`; the
  hand-written factories go away.
- The interfaces stay: they are the graph's boundaries and the seam for tests.

Why Hilt over Koin or manual wiring: compile-time validation instead of runtime
lookups, first-party ViewModel and Navigation integration, and a graph that
scales with the module count. Manual wiring already cost more than it explained.
