plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
}

tasks.register("verifyPortableAapt2Configuration") {
    group = "verification"
    description = "Verifies that a local Termux AAPT2 is selected without a tracked absolute path."

    doLast {
        val propertyName = "android.aapt2FromMavenOverride"
        val relativeToolPath = ".superpowers/sdd/tools/aapt2-16/aapt2"
        val trackedProperties = layout.projectDirectory.file("gradle.properties").asFile.readText()
        check(!Regex("(?m)^\\s*$propertyName\\s*=").containsMatchIn(trackedProperties)) {
            "$propertyName must not be persisted in gradle.properties"
        }

        val localAapt2 = layout.projectDirectory.file(relativeToolPath).asFile.canonicalFile
        if (localAapt2.canExecute()) {
            val commandLineOverride = gradle.startParameter.projectProperties[propertyName]
            check(commandLineOverride != null) {
                "./gradlew must supply -P$propertyName when $relativeToolPath is executable"
            }
            check(file(commandLineOverride).canonicalFile == localAapt2) {
                "$propertyName must resolve to the project-local $relativeToolPath"
            }
        }
    }
}
