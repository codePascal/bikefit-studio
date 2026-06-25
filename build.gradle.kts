plugins {
    kotlin("jvm") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.compose") version "1.6.11" apply false
    id("com.diffplug.spotless") version "6.25.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.7"
}

// Format + static analysis target the authored desktop code. The `core/` module is reused
// verbatim from BikefitApp and treated as vendored (its quality gate is the 952 unit tests);
// reformatting it would conflict with any future upstream sync.
spotless {
    kotlin {
        target("desktop/src/**/*.kt")
        ktfmt("0.46").kotlinlangStyle() // pure formatter, Compose-friendly (no naming lint)
    }
    kotlinGradle {
        target("*.gradle.kts", "desktop/*.gradle.kts", "core/*.gradle.kts")
        ktfmt("0.46").kotlinlangStyle()
    }
}

detekt {
    source.setFrom("desktop/src")
    buildUponDefaultConfig = true
    parallel = true
    baseline = file("detekt-baseline.xml")
}
