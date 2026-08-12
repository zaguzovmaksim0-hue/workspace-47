plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
}

tasks.register("verifyResolvedCoreVersion") {
    group = "verification"
    description = "Verifies the resolved AndroidX Core matrix used by the debug app."

    doLast {
        val expectedVersion = "1.18.0"
        val expectedModules = setOf("core", "core-ktx")
        val resolved = project(":app")
            .configurations
            .getByName("debugRuntimeClasspath")
            .incoming
            .resolutionResult
            .allComponents
            .mapNotNull { it.moduleVersion }
            .filter { it.group == "androidx.core" && it.name in expectedModules }
            .associate { it.name to it.version }

        check(resolved == expectedModules.associateWith { expectedVersion }) {
            "Expected AndroidX Core/Core-KTX $expectedVersion, resolved $resolved"
        }
    }
}

tasks.register("verifyPortableAapt2Configuration") {
    group = "verification"
    description = "Verifies the tracked, fail-closed Termux AAPT2 bootstrap contract."

    doLast {
        val propertyName = "android.aapt2FromMavenOverride"
        val relativeToolPath = ".gradle/termux-aapt2/bin/aapt2"
        val relativeRuntimePath =
            ".gradle/termux-aapt2/runtime/data/data/com.termux/files/usr/lib"
        val bootstrapRelativePath = "tools/bootstrap-termux-aapt2.sh"
        val buildDocRelativePath = "docs/building-on-termux.md"
        val trackedProperties = layout.projectDirectory.file("gradle.properties").asFile.readText()
        check(!Regex("(?m)^\\s*$propertyName\\s*=").containsMatchIn(trackedProperties)) {
            "$propertyName must not be persisted in gradle.properties"
        }

        val wrapperText = layout.projectDirectory.file("gradlew").asFile.readText()
        check(".superpowers/sdd/tools" !in wrapperText) {
            "gradlew must not depend on ignored task-runner state"
        }
        check(!Regex("/data/data/com\\.termux/files/home|/storage/emulated/0").containsMatchIn(wrapperText)) {
            "gradlew must not contain an absolute user or worktree path"
        }

        val bootstrap = layout.projectDirectory.file(bootstrapRelativePath).asFile
        check(bootstrap.isFile && bootstrap.canExecute()) {
            "$bootstrapRelativePath must be tracked and executable"
        }
        val buildDoc = layout.projectDirectory.file(buildDocRelativePath).asFile
        check(buildDoc.isFile) { "$buildDocRelativePath must document the bootstrap" }
        val buildDocText = buildDoc.readText()
        check("./tools/bootstrap-termux-aapt2.sh bootstrap" in buildDocText) {
            "$buildDocRelativePath must contain the actionable bootstrap command"
        }
        check(".gradle/termux-aapt2/" in buildDocText) {
            "$buildDocRelativePath must identify the ignored project-local install"
        }
        val ignoreText = layout.projectDirectory.file(".gitignore").asFile.readText()
        check(Regex("(?m)^\\.gradle/$").containsMatchIn(ignoreText)) {
            ".gradle/ must remain ignored"
        }

        fun run(vararg command: String): Pair<Int, String> {
            val process = ProcessBuilder(*command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            return process.waitFor() to output
        }

        val expectedContract = """
            aapt2-package|16.0.0.4-1|d35298f13ec26eee362d4e84f534b29b8e5f288b86c89d803ba4fb8ccb9784aa
            aapt2-native|2.20-android-16.0.0_r4|0921eed340fd997b3402a58acdb639e32a1445f6527427906c6f24e48a73caab
            aapt2-launcher|1
            abseil-cpp|20260526.0|e489fac652cddc39d9436141e627285f1034a545a06fbb19c420514a419ad877
            libprotobuf|2:35.1|a1ba7c7f0e5903a2134662653d3e7b9ffceaa78bdd00e07ac985e2d313ebc738
        """.trimIndent()
        val (contractExit, contractOutput) = run(bootstrap.absolutePath, "contract")
        check(contractExit == 0 && contractOutput == expectedContract) {
            "Unexpected Termux AAPT2 checksum contract: $contractOutput"
        }

        val localAapt2 = layout.projectDirectory.file(relativeToolPath).asFile.canonicalFile
        val isTermux = System.getenv("PREFIX")?.endsWith("/com.termux/files/usr") == true
        val isAarch64 = System.getProperty("os.arch") == "aarch64"
        val commandLineOverride = gradle.startParameter.projectProperties[propertyName]
        if (isTermux && isAarch64) {
            check(localAapt2.canExecute()) {
                "$relativeToolPath is missing; run ./$bootstrapRelativePath bootstrap"
            }
            check(commandLineOverride != null) {
                "./gradlew must supply -P$propertyName when $relativeToolPath is executable"
            }
            check(file(commandLineOverride).canonicalFile == localAapt2) {
                "$propertyName must resolve to the project-local $relativeToolPath"
            }
            check(layout.projectDirectory.file(relativeRuntimePath).asFile.isDirectory) {
                "The project-local AAPT2 runtime directory is missing"
            }

            val installRoot = layout.projectDirectory.file(".gradle/termux-aapt2").asFile
            val (verifyExit, verifyOutput) = run(
                bootstrap.absolutePath,
                "verify",
                "--root",
                installRoot.absolutePath,
            )
            check(verifyExit == 0 && File(verifyOutput).canonicalFile == localAapt2) {
                "Installed Termux AAPT2 failed integrity verification: $verifyOutput"
            }
            val (selfTestExit, selfTestOutput) = run(
                bootstrap.absolutePath,
                "self-test",
                "--root",
                installRoot.absolutePath,
            )
            check(selfTestExit == 0) {
                "Termux AAPT2 missing/corruption rejection failed: $selfTestOutput"
            }
        } else {
            check(commandLineOverride == null) {
                "Supported desktop hosts must use AGP's standard Maven AAPT2"
            }
        }
    }
}
