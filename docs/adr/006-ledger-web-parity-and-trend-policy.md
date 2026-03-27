# 006 - Ledger Web Parity and Trend Policy

- Status: Accepted
- Date: 2026-03-27

## Context
Ledger UI must align with `.temp` Web design, including calendar/statistics dual mode, period switching, category ranking, and daily detail interactions.

## Decision
1. Rebuild `LedgerScreen` around `.temp` layout structure instead of incremental patching old blocks.
2. Keep month/year time switching and metric switching in one screen state model.
3. Remove bar chart path and standardize to line trend rendering only.

## Why
1. Full structural alignment provides predictable visual parity and reduces style fragmentation.
2. Centralized state for view/period/metric/date keeps interactions coherent and easier to maintain.
3. Line trend is clearer on dense mobile screens and matches the requested target style.

## Consequences
- Ledger interactions now mirror the web prototype more closely.
- Trend module complexity drops by removing dual chart mode branching.
- Future enhancements can focus on data accuracy and visual polish, not layout rewiring.
