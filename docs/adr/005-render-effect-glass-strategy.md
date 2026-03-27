# 005 - RenderEffect Glass Strategy

- Status: Accepted
- Date: 2026-03-27

## Context
This Sprint continues high-fidelity migration from `.temp` WebUI to Android Compose. We need stronger iOS-style frosted glass perception while preserving current Compose architecture and performance.

## Decision
1. Keep `Modifier.glassCard` as the shared glass entry and add `RenderEffect.createBlurEffect` on Android 12+.
2. Retain gradient + multi-border layering as fallback and visual enhancement across all API levels.
3. Apply the upgraded glass style to home cards, chat header/input, and floating bottom navigation.

## Why
1. RenderEffect gives stronger, system-level blur quality on supported devices with minimal custom drawing complexity.
2. Keeping one shared modifier ensures consistent visuals and fast style iteration.
3. Layered fallback avoids visual regression on lower API devices and keeps UI parity with `.temp` references.

## Consequences
- Android 12+ devices get noticeably better frosted-glass depth.
- Lower API devices keep near-identical style through gradient and border fallback.
- Future tuning can focus on blur radius and alpha tokens without restructuring screens.
