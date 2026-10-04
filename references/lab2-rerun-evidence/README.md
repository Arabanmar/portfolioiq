# Lab 2 re-run evidence (2026-10-04)

This folder holds the exact, unedited `SellStockUseCase.java` produced by the
"Instance B (revised)" blind re-run described in `Lab2_ContextComparison.md`
("Re-run after the CLAUDE.md fix") and `Lab2_Reflection.md`, after CLAUDE.md
gained the two new UseCase-convention bullets (`userId`-first on every
`PortfolioRepository` call; `StockHolding` is immutable).

- `SellStockUseCase_InstanceB_revised.java` — the agent's output exactly as
  generated, with only its own package/import lines corrected from the
  generic `domain.*` it used to this repository's real `com.portfolioiq.domain.*`
  root (a separate, pre-existing gap in CLAUDE.md, unrelated to the two new
  rules — see the write-up for why).
- Compiling this file against the real `StockHolding.java`,
  `PortfolioRepository.java`, and `ResultCallback.java` produces exactly one
  error (`getPurchaseDate()` does not exist), down from the four errors the
  pre-fix Instance B produced. See the write-up for the full transcript and
  what that one remaining error means.

Not merged into `main`'s real source tree — this is evidence, not production
code, same as `lab2/compile-check-scratch` before it.
