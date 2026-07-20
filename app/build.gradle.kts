plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

fun org.gradle.api.provider.ProviderFactory.secretValue(name: String): String? =
    gradleProperty(name)
        .orElse(environmentVariable(name))
        .orNull
        ?.takeIf(String::isNotEmpty)

val releaseStoreFilePath = providers.secretValue("JFM_RELEASE_STORE_FILE")
val releaseStorePassword = providers.secretValue("JFM_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = providers.secretValue("JFM_RELEASE_KEY_ALIAS")
val releaseKeyPassword = providers.secretValue("JFM_RELEASE_KEY_PASSWORD")
val releaseSigningConfigured = listOf(
    releaseStoreFilePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { it != null }

val verifyReleaseSigning by tasks.registering {
    group = "verification"
    description = "Fails unless a private release signing configuration is available."

    doLast {
        if (!releaseSigningConfigured) {
            throw GradleException(
                "Private release signing is required. Set JFM_RELEASE_STORE_FILE, " +
                    "JFM_RELEASE_STORE_PASSWORD, JFM_RELEASE_KEY_ALIAS and " +
                    "JFM_RELEASE_KEY_PASSWORD as Gradle properties or environment variables.",
            )
        }
        val storeFile = rootProject.file(requireNotNull(releaseStoreFilePath))
        if (!storeFile.isFile) {
            throw GradleException("Release keystore does not exist: ${storeFile.absolutePath}")
        }
    }
}

android {
    namespace = "dev.junta.firmamobile"
    compileSdk = 36

    // Instrumented security tests must exercise the installable QA variant,
    // not the default debug variant.
    testBuildType = "qa"

    defaultConfig {
        applicationId = "dev.junta.firmamobile"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "ALLOW_QA_PROFILES", "false")
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("privateRelease") {
                storeFile = rootProject.file(requireNotNull(releaseStoreFilePath))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            buildConfigField("boolean", "ALLOW_QA_PROFILES", "true")
        }
        create("qa") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            versionNameSuffix = "-qa"
            buildConfigField("boolean", "ALLOW_QA_PROFILES", "true")
        }
        release {
            isDebuggable = false
            signingConfig = signingConfigs.findByName("privateRelease")
            buildConfigField("boolean", "ALLOW_QA_PROFILES", "false")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    sourceSets {
        getByName("qa") {
            manifest.srcFile("src/debug/AndroidManifest.xml")
            kotlin.directories.add("src/debug/java")
            res.directories.add("src/debug/res")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        // Phase 1 intentionally validates the brief-pinned API 36 contract.
        disable += "OldTargetApi"
        // The wrapper and dependency versions are an exact, compatibility-tested matrix.
        disable += setOf(
            "AndroidGradlePluginVersion",
            "GradleDependency",
            "NewerVersionAvailable",
        )
    }
}

androidComponents {
    // testBuildType selects QA for device tests. Explicitly keep both JVM
    // security-test variants available.
    beforeVariants(selector().withBuildType("debug")) { variantBuilder ->
        variantBuilder.hostTests[
            com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE
        ]?.enable = true
    }
    beforeVariants(selector().withBuildType("qa")) { variantBuilder ->
        variantBuilder.hostTests[
            com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE
        ]?.enable = true
    }
}

tasks.configureEach {
    if (name == "preReleaseBuild") {
        dependsOn(verifyReleaseSigning)
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    testImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.bouncycastle.provider)
    implementation(libs.bouncycastle.pkix)
    implementation(libs.okhttp)
    implementation(libs.xmlsec)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    "qaImplementation"(libs.androidx.compose.ui.tooling)
    "qaImplementation"(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.okhttp.tls)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.espresso.intents)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
