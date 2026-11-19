# ADR-0024 - Dependency injection in the Android app: narrow containers before Hilt

Status: accepted (2026-11-19)

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
- First slice: `ModelSettings` takes `SettingsRepository` and `ModelFactory`, and
  `PreferencesSettings` takes `SettingsRepository`, so both are tested against a
  fake instead of the whole container.
- This decision does not block the type-safe navigation or the Gradle
  modularisation work.
