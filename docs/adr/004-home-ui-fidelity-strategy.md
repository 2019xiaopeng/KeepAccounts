# 004 - Home UI Fidelity Strategy

- Status: Accepted
- Date: 2026-03-27

## Context
Current Sprint continues migrating `.temp` WebUI visual language into Android Compose. The home page needs near-pixel alignment with the provided reference screenshot, especially top header, budget card, recent list, and bottom navigation.

## Decision
1. Prioritize direct Compose layout reconstruction over introducing new UI frameworks.
2. Keep existing navigation and state architecture unchanged; perform visual-only refactor inside `HomeScreen` and `BottomNavigationBar`.
3. Use built-in Material Icons (Outlined set) plus custom typography/color tuning to match the reference style.

## Why
1. Compose-native reconstruction has the lowest integration risk and fastest iteration for screenshot-driven polishing.
2. Isolating visual changes avoids regressions in transaction flow, routing, and data persistence.
3. Outlined icons and tuned spacing/colors can approximate target aesthetics without adding asset management overhead.

## Consequences
- Home and bottom navigation become easier to iterate with screenshot-based UI reviews.
- No new dependencies are introduced in this sprint.
- If later requiring exact icon glyphs/assets, we can incrementally replace Material icons with custom vector resources.
