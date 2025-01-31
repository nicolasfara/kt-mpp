package io.github.gciatto.kt.mpp

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class JvmOnlyPlugin : AbstractKotlinProjectPlugin("jvm") {
    override fun Project.applyThisPlugin() {
        apply(plugin = kotlinPlugin())
        log("apply ${kotlinPlugin()} plugin")
        apply(plugin = "java-library")
        log("apply java-library plugin")
        configureKotlinVersionFromCatalogIfPossible()
        configureJvmVersionFromCatalogIfPossible()
        tasks.withType(KotlinCompile::class.java) { task ->
            task.kotlinOptions {
                configureKotlinOptions(targetCompilationId(task))
                configureJvmKotlinOptions(targetCompilationId(task))
            }
        }
        dependencies {
            addMainDependencies(project, target = "jdk8")
            addTestDependencies(project, target = "junit")
        }
        configure(JavaPluginExtension::class) {
            withSourcesJar()
            log("configure JVM library to include sources JAR")
        }
        addTaskAliases()
    }

    override fun PropertiesHelperExtension.declareProperties() {
        addProperty(allWarningsAsErrors)
        addProperty(ktCompilerArgs)
        addProperty(ktCompilerArgsJvm)
        addProperty(mochaTimeout)
        addProperty(versionsFromCatalog)
    }
}
