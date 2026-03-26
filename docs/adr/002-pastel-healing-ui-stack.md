# 002 - Pastel Healing UI Stack

- Status: Accepted
- Date: 2026-03-26

## Context
Sprint focused on high-fidelity UI recreation from `.temp` web prototypes, while keeping current Room + StateFlow data flow unchanged.

## Decision
1. Use custom Compose glass utilities (`Modifier.glassCard`) for repeated glassmorphism surfaces.
2. Use `accompanist-systemuicontroller` to force transparent system bars for immersive full-screen gradients.
3. Use `vico` (`compose-m3:1.13.1`) for Ledger trend chart integration.
4. Use Compose `InfiniteTransition` for typing dots in Chat as the primary loading animation in this phase.

## Why
1. Reusable visual utility reduces duplicated modifier chains and keeps style consistent.
2. Transparent system bars are required to preserve the continuous pastel background.
3. Vico provides Compose-native chart rendering with less custom drawing cost.
4. InfiniteTransition is lightweight and resource-free for MVP; Lottie remains available for richer animations later.

## Consequences
- UI style is now centralized and easier to evolve.
- Chart rendering and typing animation are production-ready for current sprint scope.
- Future sprint may replace typing dots with Lottie assets without changing screen structure.
