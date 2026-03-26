# ADR 001: State and DI Strategy

- Status: Accepted
- Date: 2026-03-26

## Context
Sprint 2 needs a practical dependency strategy and stable UI state stream for Kotlin + Jetpack Compose, with Room as the local-first source of truth.

## Decision
1. DI strategy: Use lightweight manual dependency wiring now (Application + AppContainer + ViewModel Factory), not Hilt.
2. UI state stream: Use StateFlow as the default observable state in ViewModel, not LiveData.

## Why this over alternatives

### DI: Manual Factory over Hilt (for current phase)
- Fewer moving parts in early sprint; faster iteration while architecture is still evolving.
- Keeps wiring explicit and easy to trace during initial data-flow debugging.
- Avoids adding annotation processing and module complexity before feature boundaries are stable.

### State: StateFlow over LiveData
- Native fit with Kotlin Coroutines and Flow-based Room APIs.
- Better Compose integration with collectAsState and unidirectional data flow.
- Predictable replay/current value semantics for screen state holders.

## Consequences
- Near-term simplicity and high transparency.
- If modules and dependency graph become large, a later ADR may migrate DI to Hilt.
- Existing ViewModel APIs and state contracts remain compatible with a future DI migration.
