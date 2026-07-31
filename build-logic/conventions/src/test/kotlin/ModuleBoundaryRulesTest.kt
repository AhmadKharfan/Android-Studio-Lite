import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModuleBoundaryRulesTest {

    private fun edge(from: String, to: String, configuration: String = "implementation") =
        ModuleEdge(from = from, configuration = configuration, to = to)

    @Test
    fun `feature depending on another feature is a violation`() {
        val violations = findBoundaryViolations(listOf(edge(":feature:a", ":feature:b")))
        assertEquals(1, violations.size)
        assertEquals("no-feature-to-feature", violations.single().rule)
    }

    @Test
    fun `feature depending on another feature's api module is allowed`() {
        assertTrue(findBoundaryViolations(listOf(edge(":feature:a", ":feature:b:api"))).isEmpty())
    }

    @Test
    fun `feature depending on a data module is a violation`() {
        assertEquals("no-feature-to-data", findBoundaryViolations(listOf(edge(":feature:a", ":data:local"))).single().rule)
    }

    @Test
    fun `data depending on a feature is a violation`() {
        assertEquals("no-data-to-feature", findBoundaryViolations(listOf(edge(":data:local", ":feature:a"))).single().rule)
    }

    @Test
    fun `an api module may only depend on domain`() {
        assertTrue(findBoundaryViolations(listOf(edge(":feature:a:api", ":domain"))).isEmpty())
        assertEquals(
            "api-module-scope",
            findBoundaryViolations(listOf(edge(":feature:a:api", ":core:common"))).single().rule,
        )
    }

    @Test
    fun `allowed edges produce no violations`() {
        val edges = listOf(
            edge(":feature:a", ":domain"),
            edge(":feature:a", ":designsystem"),
            edge(":feature:a", ":core:common"),
            edge(":data:local", ":domain"),
            edge(":app", ":feature:a"),
            edge(":app", ":data:local"),
        )
        assertTrue(findBoundaryViolations(edges).isEmpty())
    }

    // ---- configuration scoping ------------------------------------------------------------

    @Test
    fun `test configurations are exempt so tests may use a real implementation`() {
        val edges = listOf(
            edge(":feature:a", ":data:local", "testImplementation"),
            edge(":feature:a", ":feature:b", "testImplementation"),
            edge(":feature:a", ":data:local", "androidTestImplementation"),
        )
        assertTrue(findBoundaryViolations(edges).isEmpty())
    }

    @Test
    fun `non-implementation production configurations are still checked`() {
        // The rule must not be evadable by declaring the same edge on another production configuration.
        for (configuration in listOf("api", "compileOnly", "runtimeOnly", "debugImplementation", "releaseApi")) {
            val violations = findBoundaryViolations(listOf(edge(":feature:a", ":feature:b", configuration)))
            assertEquals(1, violations.size, "expected $configuration to be checked")
        }
    }

    @Test
    fun `tooling configurations are exempt`() {
        for (configuration in listOf("ksp", "kapt", "detektPlugins", "lintChecks", "annotationProcessor")) {
            assertFalse(isProductionConfiguration(configuration), "$configuration should be exempt")
        }
    }

    // ---- baseline -------------------------------------------------------------------------

    @Test
    fun `a baselined edge is tolerated but an unlisted one is not`() {
        val edges = listOf(edge(":feature:a", ":feature:b"), edge(":feature:c", ":feature:d"))
        val violations = findBoundaryViolations(edges, baseline = setOf(":feature:a -> :feature:b"))
        assertEquals(1, violations.size)
        assertEquals(":feature:c", violations.single().edge.from)
    }

    @Test
    fun `the shipped baseline covers exactly the known feature to feature edges`() {
        val edges = MODULE_BOUNDARY_BASELINE.map { entry ->
            val (from, to) = entry.split(" -> ")
            edge(from, to)
        }
        assertTrue(
            findBoundaryViolations(edges, MODULE_BOUNDARY_BASELINE).isEmpty(),
            "every baseline entry must silence its own edge",
        )
        assertEquals(
            MODULE_BOUNDARY_BASELINE.size,
            findBoundaryViolations(edges).size,
            "every baseline entry must correspond to a real violation, so stale entries cannot hide",
        )
    }
}

class MaterialAndConfigurationRulesTest {

    @Test
    fun `a feature depending on material is a violation`() {
        val found = findMaterialViolations(
            listOf(ExternalEdge(":feature:a:presentation", "implementation", "androidx.compose.material3", "material3")),
        )
        assertEquals(1, found.size)
        assertEquals("no-material-in-features", found.single().rule)
    }

    @Test
    fun `the design system may depend on material`() {
        // Wrapping Material is precisely its job.
        assertTrue(
            findMaterialViolations(
                listOf(ExternalEdge(":designsystem", "implementation", "androidx.compose.material3", "material3")),
            ).isEmpty(),
        )
    }

    @Test
    fun `a feature may depend on non-material libraries`() {
        assertTrue(
            findMaterialViolations(
                listOf(ExternalEdge(":feature:a:presentation", "implementation", "androidx.compose.ui", "ui")),
            ).isEmpty(),
        )
    }

    @Test
    fun `a feature may use material from a test configuration`() {
        assertTrue(
            findMaterialViolations(
                listOf(ExternalEdge(":feature:a:presentation", "testImplementation", "androidx.compose.material3", "m3")),
            ).isEmpty(),
        )
    }

    @Test
    fun `real gradle test configuration names are exempt`() {
        for (name in listOf("testImplementation", "androidTestApi", "debugUnitTestRuntimeOnly", "testDebugImplementation")) {
            assertFalse(isProductionConfiguration(name), "$name should be exempt")
        }
    }

    @Test
    fun `a configuration merely containing the letters test is not exempt`() {
        // `contest...` must not read as a test configuration and slip past every rule.
        assertTrue(isProductionConfiguration("contestImplementation"))
    }
}
