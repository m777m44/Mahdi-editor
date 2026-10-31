package com.mahdi.application

import com.mahdi.App
import com.mahdi.ExtensionFeature
import com.mahdi.TerminalFeature
import com.mahdi.ai.AiFeature
import com.mahdi.app.AppFlavour
import com.mahdi.feature.FeatureRegistry
import com.mahdi.git.GitFeature
import com.mahdi.runner.RunnerFeature

/**
 * Application entry point for the `community` distribution.
 *
 * Registers every feature, including `ExtensionFeature` provided by the bundled `:features:extensions` module.
 */
class MahdiApplication : App() {
    override fun onCreate() {
        // Publish the compile-time flavour (see app/build.gradle.kts) before any shared code runs.
        AppFlavour.init(BuildConfig.FLAVOUR)

        super.onCreate()

        // Register pluggable features
        FeatureRegistry.register(TerminalFeature())
        FeatureRegistry.register(ExtensionFeature())
        FeatureRegistry.register(RunnerFeature())
        FeatureRegistry.register(GitFeature())
        FeatureRegistry.register(AiFeature())

        // Initialize core features
        FeatureRegistry.initFeatures(this)
    }
}
