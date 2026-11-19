plugins {
    id("hopa.kotlin-jvm")
    id("hopa.ktlint")
}

dependencies {
    api(project(":contracts"))
    api(project(":routing"))

    testImplementation(libs.junit)
    testImplementation(testFixtures(project(":knowledge")))
}
