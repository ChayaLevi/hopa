plugins {
    id("hopa.kotlin-jvm")
    id("hopa.ktlint")
}

dependencies {
    api(project(":contracts"))

    testImplementation(libs.junit)
}
