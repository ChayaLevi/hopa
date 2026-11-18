plugins {
    id("hopa.kotlin-jvm")
}

// Generated knowledge only: the app catalog, the approval policy data and the
// prompt rules. Every file here except Knowledge.kt starts with "// Generated
// by"; the sources are contracts/ and services/brain/knowledge, and the build
// scripts write here. Knowledge.kt is the hand-written value and source the
// engine reads, so a fetched snapshot can replace the bundled one later.
// Nothing in this module may depend on anything else.
