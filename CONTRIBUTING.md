# Contributing to BikeFit Studio

Thanks for your interest! The [README](README.md) covers the project overview, architecture,
prerequisites, environment setup, and how to run the app — start there. This guide covers the
contributor workflow.

## Development gates

Set up your environment per the [README](README.md#setup), then — before opening a PR — make
sure these pass locally. They are exactly what CI runs:

| Gate | Command |
| --- | --- |
| Format | `./gradlew spotlessCheck` &nbsp;(auto-fix with `./gradlew spotlessApply`) |
| Static analysis | `./gradlew detekt` |
| Unit tests | `./gradlew :core:test` |
| Smoke — synthetic fit pipeline | `./gradlew :desktop:fitSmoke` |
| Smoke — real pose over a video | `./gradlew :desktop:videoSmoke -PvideoPath=<abs.mp4>` |

The video smoke uses the Python sidecar; pass `-PposePython=<venv python>` if it isn't on your
`PATH`. In VS Code, the recommended extensions and tasks are pre-wired — `Ctrl+Shift+B` runs the
formatter (see `.vscode/`).

## Code style

- Kotlin formatting is **ktfmt** (via Spotless), 100-column. There is no VS Code ktfmt
  formatter, so format-on-save is intentionally off — run `spotlessApply` (or the VS Code
  "Kotlin: format" task) instead.
- Static analysis is **detekt**, scoped to `desktop/`. Existing findings are recorded in
  `detekt-baseline.xml`; **don't expand the baseline to hide new issues** — fix them.

## The `core/` module is vendored

`core/` is reused verbatim from [BikefitApp](https://github.com/ineeve/BikefitApp) (see the
README [architecture](README.md#architecture) and [`NOTICE`](NOTICE)). Avoid editing it; if you
must, **call it out in your PR** — every change diverges from upstream and complicates future
syncs. Its quality gate is the unit tests, not formatting or static analysis.

## Submitting changes

1. Branch off `main` (`feature/…`, `fix/…`) and keep commits focused.
2. Make the development gates above pass locally.
3. Add an entry to [`CHANGELOG.md`](CHANGELOG.md) under **[Unreleased]**.
4. Open a PR against `main`. The template checklist mirrors the gates; link the issue it
   addresses (`Closes #…`). CI must be green before merge.

Bugs and ideas are welcome via the issue templates (Bug report / Feature request).

## License

By contributing you agree your contributions are licensed under the project's
[Apache-2.0 license](LICENSE). Keep the attribution in [`NOTICE`](NOTICE) intact.
