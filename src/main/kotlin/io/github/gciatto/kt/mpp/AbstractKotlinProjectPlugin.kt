package io.github.gciatto.kt.mpp

import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.logging.LogLevel
import org.gradle.configurationcache.extensions.capitalized
import org.gradle.kotlin.dsl.DependencyHandlerScope
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.KotlinJsOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinDependencyHandler
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsTargetDsl
import java.util.Locale
import kotlin.jvm.optionals.asSequence

@Suppress("TooManyFunctions")
abstract class AbstractKotlinProjectPlugin(targetName: String) : AbstractProjectPlugin() {

    companion object {
        private val SUPPORTED_TARGETS = setOf("jvm", "js", "multiplatform")
    }

    private val targetName: String = targetName.lowercase(Locale.getDefault()).also {
        require(it in SUPPORTED_TARGETS) {
            "Unsupported target: $it. Supported targets are: ${SUPPORTED_TARGETS.joinToString()}"
        }
    }

    private fun Project.getVersionFromCatalog(name: String, catalog: String? = null): VersionConstraint? {
        var catalogs = sequenceOf(project, rootProject)
            .map { it.extensions.findByType(VersionCatalogsExtension::class.java) }
            .filterNotNull()
            .flatMap { it.asSequence() }
            .toList()
        if (!catalog.isNullOrBlank()) {
            catalogs = catalogs.filter { it.name == catalog }
        }
        return catalogs.asSequence().flatMap { it.findVersion(name).asSequence() }.firstOrNull().also {
            if (it == null && catalogs.isEmpty()) {
                log(
                    message = "failed attempt to find version of `$name` in catalog" +
                        if (catalog == null) "s" else " $catalog",
                    logLevel = LogLevel.WARN
                )
            }
        }
    }

    protected fun Project.configureKotlinVersionFromCatalogIfPossible() {
        val catalog = getOptionalProperty("versionsFromCatalog")?.takeIf { it.isNotBlank() }
        val version = getVersionFromCatalog("kotlin", catalog)
        version?.requiredVersion?.let { kotlinVersion(it) }
    }

    protected fun Project.configureJvmVersionFromCatalogIfPossible() {
        val catalog = getOptionalProperty("versionsFromCatalog")?.takeIf { it.isNotBlank() }
        val version = getVersionFromCatalog("jvm", catalog)
        version?.requiredVersion?.let { jvmVersion(it) }
    }

    protected fun Project.configureNodeVersionFromCatalogIfPossible() {
        val catalog = getOptionalProperty("versionsFromCatalog")?.takeIf { it.isNotBlank() }
        val version = getVersionFromCatalog("node", catalog)
        nodeVersion(provider { version?.requiredVersion }, getOptionalProperty("nodeVersion"))
    }

    protected fun kotlinPlugin(name: String = targetName) =
        "org.jetbrains.kotlin.$name"

    context(Project)
    protected fun KotlinJvmOptions.configureJvmKotlinOptions(target: String) {
        val ktCompilerArgsJvm = getProperty("ktCompilerArgsJvm").split(";").filter { it.isNotBlank() }
        if (ktCompilerArgsJvm.isNotEmpty()) {
            freeCompilerArgs += ktCompilerArgsJvm
            log("add JVM-specific free compiler args for $target: ${ktCompilerArgsJvm.joinToString()}")
        }
    }

    context(Project)
    protected fun KotlinJsOptions.configureJsKotlinOptions(target: String) {
        main = "noCall"
        log("configure kotlin JS compiler to avoid calling main() in $target")
        val ktCompilerArgsJs = getProperty("ktCompilerArgsJs").split(";").filter { it.isNotBlank() }
        if (ktCompilerArgsJs.isNotEmpty()) {
            freeCompilerArgs += ktCompilerArgsJs
            log("add JS-specific free compiler args for $target: ${ktCompilerArgsJs.joinToString()}")
        }
    }

    context(Project)
    protected fun KotlinCommonOptions.configureKotlinOptions(target: String) {
        allWarningsAsErrors = getBooleanProperty("allWarningsAsErrors", default = true)
        if (allWarningsAsErrors) {
            log("consider all warnings as errors when compiling Kotlin sources in $target")
        }
        val ktCompilerArgs = getProperty("ktCompilerArgs").split(";").filter { it.isNotBlank() }
        if (ktCompilerArgs.isNotEmpty()) {
            freeCompilerArgs += ktCompilerArgs
            log("add free compiler args for $target: ${ktCompilerArgs.joinToString()}")
        }
    }

    private fun Any.toDependencyNotation(): String = when (this) {
        is Dependency -> listOfNotNull(group, name, version).joinToString(":")
        else -> toString()
    }

    private fun DependencyScope.addMainDependencies(project: Project, target: String, skipBom: Boolean) {
        val kotlinStdlib = kotlin("stdlib-$target")
        api(kotlinStdlib)
        project.log("add api dependency to ${kotlinStdlib.toDependencyNotation()}")
        if (!skipBom) {
            val kotlinBom = kotlin("bom")
            implementation(kotlinBom)
            project.log("add implementation dependency to ${kotlinBom.toDependencyNotation()}")
        }
    }

    protected fun DependencyHandlerScope.addMainDependencies(
        project: Project,
        target: String,
        skipBom: Boolean = false
    ) = DependencyScope.of(this).addMainDependencies(project, target, skipBom)

    protected fun KotlinDependencyHandler.addMainDependencies(
        project: Project,
        target: String,
        skipBom: Boolean = false
    ) = DependencyScope.of(this).addMainDependencies(project, target, skipBom)

    private fun DependencyScope.addTestDependencies(project: Project, target: String, skipAnnotations: Boolean) {
        val testLib = kotlin("test-$target")
        test(testLib)
        project.log("add test dependency to ${testLib.toDependencyNotation()}")
        if (!skipAnnotations) {
            val annotationsLib = kotlin("test-annotations-$target")
            test(annotationsLib)
            project.log("add test dependency to ${annotationsLib.toDependencyNotation()}")
        }
    }

    protected fun KotlinDependencyHandler.addTestDependencies(
        project: Project,
        target: String = targetName,
        skipAnnotations: Boolean = false
    ) = DependencyScope.of(this).addTestDependencies(project, target, skipAnnotations)

    protected fun DependencyHandlerScope.addTestDependencies(
        project: Project,
        target: String = targetName,
        skipAnnotations: Boolean = false
    ) = DependencyScope.of(this).addTestDependencies(project, target, skipAnnotations)

    protected fun Project.addTaskAliases() {
        tasks.register("${targetName}Test") {
            it.group = "verification"
            it.dependsOn("test")
            log("add ${it.path} task as an alias for ${it.sibling("test")}")
        }
        tasks.register("${targetName}MainClasses") {
            it.group = "build"
            it.dependsOn("mainClasses")
            log("add ${it.path} task as an alias for ${it.sibling("mainClasses")}")
        }
        tasks.register("${targetName}TestClasses") {
            it.group = "build"
            it.dependsOn("testClasses")
            log("add ${it.path} task as an alias for ${it.sibling("testClasses")}")
        }
    }

    protected fun KotlinTarget.targetCompilationId(compilation: KotlinCompilation<*>): String =
        "${name}${compilation.compilationName.capitalized()}"

    protected fun targetCompilationId(task: KotlinCompile<*>): String =
        task.name.replace("compile", "")

    context (Project, KotlinJsTargetDsl)
    protected fun configureNodeJs() {
        nodejs {
            log("configure kotlin JS to target NodeJS")
            testTask {
                useMocha {
                    log("use Mocha as JS test framework")
                    timeout = getProperty("mochaTimeout")
                    log("set Mocha per-test-case timeout to $timeout")
                }
            }
        }
    }
}
