import java.net.IDN
import java.util.Base64
import java.util.Locale

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

fun org.gradle.api.provider.ProviderFactory.secretValue(name: String): String? =
    gradleProperty(name)
        .orElse(environmentVariable(name))
        .orNull
        ?.takeIf(String::isNotEmpty)

fun quotedBuildConfigString(value: String): String {
    require(value.none(Char::isISOControl)) { "Control characters are not allowed in BuildConfig strings." }
    return buildString(value.length + 2) {
        append('"')
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                else -> append(character)
            }
        }
        append('"')
    }
}

fun quotedBuildConfigText(value: String): String = buildString(value.length + 2) {
    append('"')
    value.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            else -> {
                if (character.code < 0x20) {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
    }
    append('"')
}

fun validRelayHost(value: String): Boolean {
    if (value.isBlank() || value.length > 253 || value.any(Char::isISOControl)) return false
    if (value != value.lowercase(Locale.ROOT) || value.endsWith('.') || value == "localhost") return false
    if (value.contains(':') || value.matches(Regex("[0-9.]+"))) return false
    val ascii = runCatching { IDN.toASCII(value, IDN.USE_STD3_ASCII_RULES) }.getOrNull()
        ?: return false
    if (ascii != value || '.' !in ascii) return false
    return ascii.split('.').all { label ->
        label.isNotEmpty() && label.length <= 63 &&
            label.first().isLetterOrDigit() && label.last().isLetterOrDigit()
    }
}

fun parseRelayPins(value: String): List<String>? {
    if (value.any(Char::isISOControl)) return null
    val pins = value.split(',').map(String::trim).filter(String::isNotEmpty).distinct()
    if (pins.size < 2) return null
    val valid = pins.all { pin ->
        if (!pin.startsWith("sha256/")) return@all false
        val encoded = pin.removePrefix("sha256/")
        val decoded = runCatching { Base64.getDecoder().decode(encoded) }.getOrNull()
            ?: return@all false
        decoded.size == 32 && Base64.getEncoder().encodeToString(decoded) == encoded
    }
    return pins.takeIf { valid }
}

val siteProfileCatalogFile = rootProject.layout.projectDirectory.file("config/site_profiles_v1.json")
val siteProfileCatalogJson = providers.fileContents(siteProfileCatalogFile).asText.get()

val qaRelayHostInput = providers.secretValue("JFM_WS024_QA_RELAY_HOST")
val qaRelayPortInput = providers.secretValue("JFM_WS024_QA_RELAY_PORT")
val qaRelayPinsInput = providers.secretValue("JFM_WS024_QA_RELAY_SPKI_PINS")
val qaTunnelTupleSupplied = listOf(qaRelayHostInput, qaRelayPortInput, qaRelayPinsInput).any { it != null }
val qaRelayPortParsed = qaRelayPortInput?.toIntOrNull()
val qaRelayPort = qaRelayPortParsed ?: 443
val qaRelayPins = qaRelayPinsInput?.let(::parseRelayPins)
val qaRelayPortValid = qaRelayPortInput == null || qaRelayPortParsed != null
val qaTunnelConfigured = qaRelayHostInput?.let(::validRelayHost) == true &&
    qaRelayPortValid &&
    qaRelayPort in 1..65535 &&
    qaRelayPins != null
if (qaTunnelTupleSupplied && !qaTunnelConfigured) {
    throw GradleException(
        "Invalid WS024 QA relay configuration. Supply a canonical host, a port in 1..65535 " +
            "and at least two distinct canonical SHA-256 SPKI pins.",
    )
}
val qaRelayHost = qaRelayHostInput.takeIf { qaTunnelConfigured }.orEmpty()
val qaRelayPinsValue = qaRelayPins.takeIf { qaTunnelConfigured }.orEmpty().joinToString(",")

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
        buildConfigField(
            "String",
            "SITE_PROFILE_CATALOG_JSON",
            quotedBuildConfigText(siteProfileCatalogJson),
        )
        buildConfigField("boolean", "ALLOW_QA_PROFILES", "false")
        buildConfigField("boolean", "ENABLE_WS024_QA_TUNNEL", "false")
        buildConfigField("String", "WS024_QA_RELAY_HOST", quotedBuildConfigString(""))
        buildConfigField("int", "WS024_QA_RELAY_PORT", "443")
        buildConfigField("String", "WS024_QA_RELAY_SPKI_PINS", quotedBuildConfigString(""))
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
            buildConfigField("boolean", "ENABLE_WS024_QA_TUNNEL", "false")
            buildConfigField("String", "WS024_QA_RELAY_HOST", quotedBuildConfigString(""))
            buildConfigField("int", "WS024_QA_RELAY_PORT", "443")
            buildConfigField("String", "WS024_QA_RELAY_SPKI_PINS", quotedBuildConfigString(""))
        }
        create("qa") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            versionNameSuffix = "-qa"
            buildConfigField("boolean", "ALLOW_QA_PROFILES", "true")
            buildConfigField("boolean", "ENABLE_WS024_QA_TUNNEL", qaTunnelConfigured.toString())
            buildConfigField("String", "WS024_QA_RELAY_HOST", quotedBuildConfigString(qaRelayHost))
            buildConfigField("int", "WS024_QA_RELAY_PORT", qaRelayPort.toString())
            buildConfigField("String", "WS024_QA_RELAY_SPKI_PINS", quotedBuildConfigString(qaRelayPinsValue))
        }
        release {
            isDebuggable = false
            signingConfig = signingConfigs.findByName("privateRelease")
            buildConfigField("boolean", "ALLOW_QA_PROFILES", "false")
            buildConfigField("boolean", "ENABLE_WS024_QA_TUNNEL", "false")
            buildConfigField("String", "WS024_QA_RELAY_HOST", quotedBuildConfigString(""))
            buildConfigField("int", "WS024_QA_RELAY_PORT", "443")
            buildConfigField("String", "WS024_QA_RELAY_SPKI_PINS", quotedBuildConfigString(""))
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

val ws024ExternalHarnessActive = listOf(
    "JFM_TUNNEL_TEST_RELAY_PORT",
    "JFM_TUNNEL_TEST_OUTER_CA_PEM",
    "JFM_TUNNEL_TEST_INNER_CA_PEM",
    "JFM_TUNNEL_TEST_RESULT_FILE",
).any { providers.environmentVariable(it).isPresent }

if (ws024ExternalHarnessActive) {
    tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
        if (name == "testDebugUnitTest") {
            outputs.upToDateWhen { false }
            outputs.cacheIf("external WS024 harness inputs are ephemeral") { false }
        }
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
