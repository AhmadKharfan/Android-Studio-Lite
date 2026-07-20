package com.ahmadkharfan.androidstudiolite.feature.buildrun

import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ModuleModel
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ModuleType
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ProjectModel
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.VariantModel

/**
 * Picks the Android application module and concrete Gradle variant to run.
 *
 * Many real projects (especially KMP) do not use `:app` / bare `debug` — e.g. MENA-mobile uses
 * `:composeApp` with `developmentDebug` / `stagingDebug` / `productionDebug`. Hardcoding `:app`
 * still lets root `assembleDebug` succeed, but install needs the real module's applicationId and
 * flavored APK paths need a variant the worker can match.
 *
 * Defaults match Android Studio's Run configuration: prefer a **debug** build type, and among
 * flavored debug variants prefer development/dev-style environments.
 */
object RunTargetResolver {

    /**
     * Flavor names commonly used for local/dev Run configs, ordered most → least preferred
     * (Android Studio typically lands on the first "development"-like flavor + debug).
     */
    private val PREFERRED_FLAVORS = listOf(
        "development", "dev", "local", "debug", "staging", "qa", "demo", "alpha", "beta", "free",
    )

    fun resolveAppModule(project: ProjectModel): ModuleModel? = resolveAppModule(project.modules)

    fun resolveAppModule(modules: List<ModuleModel>): ModuleModel? {
        modules.firstOrNull { it.path == ":app" && it.type == ModuleType.ANDROID_APP }
            ?.let { return it }
        modules.firstOrNull { it.type == ModuleType.ANDROID_APP }?.let { return it }
        // Fallback for parsers that missed catalog-alias androidApplication plugins.
        return modules.firstOrNull { path ->
            val p = path.path.lowercase()
            p == ":composeapp" || p.endsWith(":composeapp") ||
                p == ":androidapp" || p.endsWith(":androidapp") ||
                path.name.equals("app", ignoreCase = true)
        }
    }

    /** Android Studio-like default for the Run button: best debug variant for [module]. */
    fun preferredRunVariant(module: ModuleModel?): String = resolveVariant(module, "debug")

    /**
     * Release sibling of the current Build Variants selection (same product flavors), falling back
     * to the preferred flavored release. Used by one-shot "Build APK (release)" without changing Run.
     */
    fun resolveReleaseVariant(module: ModuleModel?, currentlySelected: String): String {
        val variants = module?.variants.orEmpty()
        if (variants.isEmpty()) return "release"
        val current = variants.firstOrNull { it.name.equals(currentlySelected, ignoreCase = true) }
        if (current != null) {
            if (current.buildType.equals("release", ignoreCase = true)) return current.name
            val sibling = variants.firstOrNull { candidate ->
                candidate.buildType.equals("release", ignoreCase = true) &&
                    candidate.flavors.map { it.lowercase() } == current.flavors.map { it.lowercase() }
            }
            if (sibling != null) return sibling.name
        }
        return resolveVariant(module, "release")
    }

    /**
     * Maps the UI selection (often bare `debug` / `release`) onto a real variant declared by
     * [module]. Prefer a `development` / `dev` flavored match when several exist for that build type.
     */
    fun resolveVariant(module: ModuleModel?, requested: String): String {
        if (module == null) return requested
        val variants = module.variants
        if (variants.isEmpty()) return requested
        if (variants.any { it.name.equals(requested, ignoreCase = true) }) return requested

        val requestedBuildType = requestedBuildType(requested)
        val matchingBuildType = variants.filter { it.buildType.equals(requestedBuildType, ignoreCase = true) }
        if (matchingBuildType.isNotEmpty()) {
            return matchingBuildType.minWith(variantComparator).name
        }

        // Bare / stale name that doesn't match any build type — fall back to preferred Run variant.
        val debugVariants = variants.filter { it.buildType.equals("debug", ignoreCase = true) }
        return (debugVariants.ifEmpty { variants }).minWith(variantComparator).name
    }

    /**
     * Keeps an explicit concrete selection when it still exists; otherwise remaps bare/`debug`/
     * stale names onto [preferredRunVariant] (never onto release unless that is all that exists).
     */
    fun resolveSelectedVariant(module: ModuleModel?, currentlySelected: String): String {
        val available = module?.variants.orEmpty()
        if (available.isEmpty()) return currentlySelected.ifBlank { "debug" }
        if (available.any { it.name.equals(currentlySelected, ignoreCase = true) }) {
            return available.first { it.name.equals(currentlySelected, ignoreCase = true) }.name
        }
        return resolveVariant(module, currentlySelected.ifBlank { "debug" })
    }

    fun availableVariantNames(module: ModuleModel?): List<String> {
        val variants = module?.variants.orEmpty()
        if (variants.isEmpty()) return listOf("debug", "release")
        return variants.sortedWith(variantComparator).map { it.name }
    }

    fun isDebugVariant(variantName: String): Boolean {
        val n = variantName.trim()
        if (n.equals("debug", ignoreCase = true)) return true
        if (n.contains("release", ignoreCase = true)) return false
        return n.endsWith("Debug", ignoreCase = true)
    }

    fun isReleaseVariant(variantName: String): Boolean {
        val n = variantName.trim()
        if (n.equals("release", ignoreCase = true)) return true
        if (n.contains("debug", ignoreCase = true)) return false
        return n.endsWith("Release", ignoreCase = true)
    }

    private fun requestedBuildType(requested: String): String = when {
        requested.equals("debug", ignoreCase = true) ||
            requested.endsWith("Debug", ignoreCase = true) -> "debug"
        requested.equals("release", ignoreCase = true) ||
            requested.endsWith("Release", ignoreCase = true) -> "release"
        else -> requested
    }

    private fun flavorRank(variant: VariantModel): Int {
        val names = variant.flavors.map { it.lowercase() }.ifEmpty {
            listOfNotNull(flavorPrefixFromName(variant.name)?.lowercase())
        }
        var best = PREFERRED_FLAVORS.size
        for (name in names) {
            val idx = PREFERRED_FLAVORS.indexOf(name)
            if (idx in 0 until best) best = idx
        }
        return best
    }

    /** e.g. `developmentDebug` → `development` when flavors weren't parsed. */
    private fun flavorPrefixFromName(name: String): String? {
        val suffixes = listOf("Debug", "Release", "Benchmark")
        for (suffix in suffixes) {
            if (name.endsWith(suffix) && name.length > suffix.length) {
                return name.removeSuffix(suffix).replaceFirstChar { it.lowercase() }
            }
        }
        return null
    }

    private val variantComparator = Comparator<VariantModel> { a, b ->
        val byType = buildTypeRank(a.buildType).compareTo(buildTypeRank(b.buildType))
        if (byType != 0) return@Comparator byType
        val byFlavor = flavorRank(a).compareTo(flavorRank(b))
        if (byFlavor != 0) return@Comparator byFlavor
        a.name.compareTo(b.name, ignoreCase = true)
    }

    private fun buildTypeRank(buildType: String): Int = when {
        buildType.equals("debug", ignoreCase = true) -> 0
        buildType.equals("release", ignoreCase = true) -> 1
        else -> 2
    }
}
