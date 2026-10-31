rootProject.name = "Mahdi-Editor"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://jitpack.io")
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        maven("https://s01.oss.sonatype.org/content/repositories/snapshots")
        maven("https://repo.eclipse.org/content/groups/releases/")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        maven("https://s01.oss.sonatype.org/content/repositories/snapshots")
        maven("https://repo.eclipse.org/content/groups/releases/")
    }
}

include(":app", ":core:main", ":core:components", ":core:resources")

include(":features:terminal", ":features:extensions", ":features:runner", ":features:git")

include(":baselineprofile", ":benchmark", ":benchmark2")

// Termux terminal emulator/view are built from the in-tree sources with NDK 28
// (16 KB page size aligned) instead of the prebuilt JitPack AARs, whose
// libtermux.so was only 4 KB (0x1000) aligned and failed the Android 15+
// 16 KB page size requirement.
include(":terminal-emulator", ":terminal-view")

include(":features:terminal:proot")
include(":features:terminal:link2symlink")
include(":features:terminal:mahdi-cli")


// محرّك التحرير (soraX) مضمّن داخل المستودع، لا submodule.
// The editor engine (soraX) is vendored in this repository; no submodule checkout is required.
if (!file("soraX/settings.gradle.kts").exists()) {
    throw GradleException(
        """
        The bundled editor engine 'soraX' is missing or incomplete.

        It should be part of the repository checkout. Restore it with:
            git checkout -- soraX
        or copy it from the release archive.
        """
            .trimIndent()
    )
}

// The included soraX build is a standalone Gradle build, so it needs the SDK path in
// its own local.properties. Mirror the one from this project automatically.
if (!file("soraX/local.properties").exists()) {
    val parentProps = file("local.properties")
    if (parentProps.exists()) {
        try {
            parentProps.copyTo(file("soraX/local.properties"))
        } catch (ignored: Exception) {
            // fall back to ANDROID_HOME / ANDROID_SDK_ROOT when copying is not possible
        }
    }
}

includeBuild("soraX") {
    dependencySubstitution {
        // The "soraX:<module>" coordinates are placeholders (see libs.versions.toml).
        // They are never published or resolved from a repository - every Mahdi build
        // compiles these modules directly from the bundled soraX sources instead.
        substitute(module("soraX:editor")).using(project(":editor"))
        substitute(module("soraX:editor-lsp")).using(project(":editor-lsp"))
        substitute(module("soraX:language-textmate")).using(project(":language-textmate"))
        substitute(module("soraX:oniguruma-native")).using(project(":oniguruma-native"))
    }
}
include(":features:ai")
