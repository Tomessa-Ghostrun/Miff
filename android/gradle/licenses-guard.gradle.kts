// Licence guard (ticket docs/tickets/reglages-a-propos.md, criterion AP15).
//
// The screen Settings > "A propos et aide" > "Licences et attributions" lists what the app of release ships, from the file
// app/src/main/assets/licenses/licenses.json. This task keeps that file honest: it resolves the runtime classpath of the RELEASE variant (the one that is
// distributed: no test, no debug-only dependency) and FAILS when a module of it is not covered by any entry of the file. Adding a dependency without writing its
// licence therefore turns the CI red, so that the screen never becomes false without anybody noticing.
//
// It is wired in front of testDebugUnitTest (the "Unit tests" step of the CI), so there is nothing else to run. By hand: ./gradlew checkLicenses
// A module is covered when its "group:artifact" STARTS WITH one of the "modules" prefixes of an entry (so "androidx." covers every AndroidX library).
// A version that differs from the one written in the file is only a WARNING (Dependabot bumps versions every week: failing on that would turn every one of
// its pull requests red); the licence of a library does not change with a patch version, but read it when a MAJOR version changes.
//
// No network, no secret: the dependencies are already resolved by the build itself.

import groovy.json.JsonSlurper
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult

val licensesAppProject = project(":app")
val licensesInventory = layout.projectDirectory.file("app/src/main/assets/licenses/licenses.json").asFile
// Read lazily: the app project is evaluated AFTER the root one, so its configurations do not exist yet when this script runs.
val licensesReleaseGraph =
    provider {
        licensesAppProject.configurations
            .getByName("releaseRuntimeClasspath")
            .incoming.resolutionResult.rootComponent
            .get()
    }

val checkLicenses =
    tasks.register("checkLicenses") {
        group = "verification"
        description = "Fails when a library shipped in the release build has no licence entry in licenses.json (criterion AP15)."
        val root = licensesReleaseGraph
        val file = licensesInventory
        inputs.file(file)
        // The resolved graph can change without any file changing: always run (it takes a moment).
        outputs.upToDateWhen { false }
        doLast {
            val modules = sortedMapOf<String, String>()
            val seen = mutableSetOf<ComponentIdentifier>()

            fun walk(component: ResolvedComponentResult) {
                if (!seen.add(component.id)) return
                val id = component.id
                if (id is ModuleComponentIdentifier) modules["${id.group}:${id.module}"] = id.version
                component.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach { walk(it.selected) }
            }
            walk(root.get())

            @Suppress("UNCHECKED_CAST")
            val inventory = JsonSlurper().parse(file) as Map<String, Any?>

            @Suppress("UNCHECKED_CAST")
            val entries = inventory["entries"] as List<Map<String, Any?>>
            val prefixes = entries.flatMap { (it["modules"] as? List<*>).orEmpty().map { prefix -> prefix as String } }

            val missing = modules.keys.filter { coordinate -> prefixes.none { coordinate.startsWith(it) } }
            if (missing.isNotEmpty()) {
                throw GradleException(
                    "Licence manquante pour " + missing.size + " bibliotheque(s) livree(s) dans le build de release :\n" +
                        missing.joinToString("\n") { "  - $it:${modules[it]}" } +
                        "\nAjoute son prefixe dans la liste \"modules\" d'une entree de " + file.path +
                        " (ou cree une entree avec son texte de licence dans assets/licenses/texts/), puis relance. " +
                        "La licence se lit dans le POM de la bibliotheque ou son depot ; ne jamais ecrire \"conforme\" ni \"relu\".",
                )
            }
            entries.forEach { entry ->
                val of = entry["versionOf"] as? String ?: return@forEach
                val written = entry["version"] as? String ?: return@forEach
                val actual = modules[of]
                if (actual != null && !written.endsWith(actual)) {
                    logger.warn(
                        "licenses.json : l'entree \"" + entry["id"] + "\" annonce la version \"" + written + "\" mais $of est en $actual : " +
                            "mets la version a jour dans " + file.name + " (avertissement seulement).",
                    )
                }
            }
            logger.lifecycle("checkLicenses : " + modules.size + " modules de release, tous couverts par " + file.name + ".")
        }
    }

licensesAppProject.tasks.matching { it.name == "testDebugUnitTest" }.configureEach { dependsOn(checkLicenses) }
