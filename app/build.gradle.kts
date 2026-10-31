import java.util.Properties

plugins {
    alias(libs.plugins.android.baselineprofile)
    alias(libs.plugins.android.application)
    alias(libs.plugins.ktfmt)
}

val distributionDimension = "distribution"
val communityFlavor = "community"

android {
    namespace = "com.mahdi.application"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.mahdi.editor"
        minSdk = 26

        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // versioning
        versionCode = 11
        versionName = "2.7.1"
        vectorDrawables { useSupportLibrary = true }
    }

    flavorDimensions += distributionDimension

    productFlavors {
        // Default, fully featured distribution (GitHub, F-Droid, IzzyOnDroid).
        create(communityFlavor) {
            dimension = distributionDimension
            isDefault = true
            // Published at runtime as com.mahdi.app.AppFlavour via AppFlavour.init(BuildConfig.FLAVOUR).
            buildConfigField("String", "FLAVOUR", "\"$communityFlavor\"")
        }

    }

    buildFeatures {
        viewBinding = true
        compose = false
        buildConfig = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging { jniLibs { useLegacyPackaging = true } }

    signingConfigs {
        create("release") {
            // Signing material is resolved in this order:
            //   1. $MAHDI_SIGNING_PROPERTIES   (path to a signing.properties file)
            //   2. <repo>/signing.properties   (see signing.properties.example)
            //   3. the bundled testkey         (so that assembleRelease always produces an installable APK)
            val isGitHubActions = System.getenv("GITHUB_ACTIONS") == "true"
            val propertiesFilePath =
                System.getenv("MAHDI_SIGNING_PROPERTIES")
                    ?: if (isGitHubActions) "/tmp/signing.properties" else rootProject.file("signing.properties").absolutePath

            val propertiesFile = File(propertiesFilePath)
            val testKey = rootProject.file("app/testkey.keystore")

            if (propertiesFile.exists()) {
                val properties = Properties()
                propertiesFile.inputStream().use { properties.load(it) }
                keyAlias = properties["keyAlias"] as String?
                keyPassword = properties["keyPassword"] as String?
                storePassword = properties["storePassword"] as String?
                storeFile =
                    (properties["storeFile"] as String?)?.let { File(it) }
                        ?: if (isGitHubActions) File("/tmp/mahdi.keystore") else null
            } else {
                println("Mahdi-Editor: signing.properties not found at $propertiesFilePath - signing release builds with the bundled test key.")
            }

            if (storeFile == null || storeFile?.exists() != true) {
                keyAlias = "testkey"
                keyPassword = "testkey"
                storePassword = "testkey"
                storeFile = testKey
            }
        }
        getByName("debug") {
            storeFile = file(layout.buildDirectory.dir("../testkey.keystore"))
            storePassword = "testkey"
            keyAlias = "testkey"
            keyPassword = "testkey"
        }
    }

    buildTypes {
        release {
            // Minification stays off so that extensions can load and debug builds stay debuggable.
            isMinifyEnabled = false
            isShrinkResources = false
            isCrunchPngs = false

            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            signingConfig = signingConfigs.getByName("release")
        }

        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
            resValue("string", "app_name", "Mahdi-Debug")
        }

        create("benchmark") {
            initWith(buildTypes.getByName("release"))
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
    }
}

kotlin { jvmToolchain(21) }

dependencies {
    androidTestImplementation(libs.androidx.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.uiautomator)

    implementation(libs.androidx.profileinstaller)
    coreLibraryDesugaring(libs.desugar)

    baselineProfile(project(":baselineprofile"))
    implementation(project(":core:main"))
    implementation(project(":core:resources"))
    implementation(libs.androidx.appcompat)
    implementation(project(":features:terminal"))
    implementation(project(":features:runner"))
    implementation(project(":features:git"))
    implementation(project(":features:ai"))

    // The extensions feature module is bundled with the community distribution.
    "communityImplementation"(project(":features:extensions"))
}
