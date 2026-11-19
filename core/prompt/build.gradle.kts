plugins {
    id("hopa.kotlin-jvm")
    id("hopa.ktlint")
}

dependencies {
    api(project(":contracts"))
    api(project(":catalog"))

    testImplementation(libs.junit)
    testImplementation(testFixtures(project(":catalog")))
}
