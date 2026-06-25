# Pull Request

<!-- Sections that do not apply can be omitted -->

## Summary

<!-- What does this PR change, and why? -->

## Related issue

<!-- e.g. Closes #123 -->

## Type of change

- [ ] Bug fix
- [ ] New feature
- [ ] Refactor / cleanup
- [ ] Documentation
- [ ] Build / CI / tooling

## Unit tests

<!-- Which unit tests did you add or update, and why? -->

## Functional tests

<!--
How did you verify the behavior? e.g. ran the app (`./gradlew :desktop:run`), the synthetic
pipeline (`./gradlew :desktop:fitSmoke`), or the real pose pipeline
(`./gradlew :desktop:videoSmoke -PvideoPath=…`).
-->

## AI tool usage disclosure

<!-- Check one and add a short note. Tier ≈ how much of the change AI produced. -->

- [ ] None
- [ ] Minor — autocomplete or occasional snippets
- [ ] Moderate — AI drafted parts, reviewed and adapted by me
- [ ] Major — AI produced most of the change

## Checklist

- [ ] Pipeline passes
- [ ] Fixed new static-analysis findings instead of expanding `detekt-baseline.xml`
- [ ] Added a `CHANGELOG.md` entry under **[Unreleased]**
- [ ] Updated documentation (`README`, code comments) where relevant
- [ ] Flagged any `core/` edits in the summary (it's vendored from upstream BikefitApp)
