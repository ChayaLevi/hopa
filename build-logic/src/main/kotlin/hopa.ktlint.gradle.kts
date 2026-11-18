// The style gate, applied by the module that is held to it. In 2026 this is a
// plugin a module opts into, not a `subprojects { if (name == "...") }` block in
// a root build: that block keyed on a module name and silently stopped applying
// when the module was renamed.
plugins {
    id("org.jlleitschuh.gradle.ktlint")
}
