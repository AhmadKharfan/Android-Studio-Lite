package com.ahmadkharfan.androidstudiolite.feature.buildrun

import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ModuleModel
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ModuleType
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.VariantModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RunTargetResolverTest {

    private fun module(
        path: String,
        type: ModuleType,
        variants: List<VariantModel> = emptyList(),
    ) = ModuleModel(
        path = path,
        name = path.removePrefix(":"),
        type = type,
        moduleDir = File("/tmp/${path.trimStart(':')}"),
        variants = variants,
    )

    private fun menaLikeVariants() = listOf(


        VariantModel("productionRelease", "release", listOf("production")),
        VariantModel("stagingRelease", "release", listOf("staging")),
        VariantModel("developmentRelease", "release", listOf("development")),
        VariantModel("productionDebug", "debug", listOf("production")),
        VariantModel("stagingDebug", "debug", listOf("staging")),
        VariantModel("developmentDebug", "debug", listOf("development")),
    )

    @Test
    fun `prefers colon-app android application module`() {
        val modules = listOf(
            module(":lib", ModuleType.ANDROID_LIBRARY),
            module(":app", ModuleType.ANDROID_APP),
            module(":composeApp", ModuleType.ANDROID_APP),
        )
        assertEquals(":app", RunTargetResolver.resolveAppModule(modules)?.path)
    }

    @Test
    fun `resolves arbitrarily named android application module by type`() {
        val modules = listOf(
            module(":core", ModuleType.JVM),
            module(":design", ModuleType.ANDROID_LIBRARY),
            module(":mobile", ModuleType.ANDROID_APP, variants = menaLikeVariants()),
        )
        assertEquals(":mobile", RunTargetResolver.resolveAppModule(modules)?.path)
    }

    @Test
    fun `falls back to composeApp when no colon-app`() {
        val modules = listOf(
            module(":core", ModuleType.JVM),
            module(
                ":composeApp",
                ModuleType.ANDROID_APP,
                variants = listOf(
                    VariantModel("developmentDebug", "debug", listOf("development")),
                    VariantModel("productionDebug", "debug", listOf("production")),
                ),
            ),
        )
        assertEquals(":composeApp", RunTargetResolver.resolveAppModule(modules)?.path)
    }

    @Test
    fun `maps bare debug onto preferred development flavored variant`() {
        val app = module(":composeApp", ModuleType.ANDROID_APP, menaLikeVariants())
        assertEquals("developmentDebug", RunTargetResolver.resolveVariant(app, "debug"))
        assertEquals("developmentRelease", RunTargetResolver.resolveVariant(app, "release"))
        assertEquals("stagingDebug", RunTargetResolver.resolveVariant(app, "stagingDebug"))
        assertEquals("developmentDebug", RunTargetResolver.preferredRunVariant(app))
    }

    @Test
    fun `available variants list debug before release and preferred flavors first`() {
        val app = module(":composeApp", ModuleType.ANDROID_APP, menaLikeVariants())
        assertEquals(
            listOf(
                "developmentDebug",
                "stagingDebug",
                "productionDebug",
                "developmentRelease",
                "stagingRelease",
                "productionRelease",
            ),
            RunTargetResolver.availableVariantNames(app),
        )
    }

    @Test
    fun `resolveSelectedVariant keeps explicit concrete choice`() {
        val app = module(":composeApp", ModuleType.ANDROID_APP, menaLikeVariants())
        assertEquals(
            "stagingDebug",
            RunTargetResolver.resolveSelectedVariant(app, "stagingDebug"),
        )
        assertEquals(
            "developmentDebug",
            RunTargetResolver.resolveSelectedVariant(app, "debug"),
        )
    }

    @Test
    fun `resolveReleaseVariant keeps the selected flavor`() {
        val app = module(":composeApp", ModuleType.ANDROID_APP, menaLikeVariants())
        assertEquals(
            "stagingRelease",
            RunTargetResolver.resolveReleaseVariant(app, "stagingDebug"),
        )
        assertEquals(
            "developmentRelease",
            RunTargetResolver.resolveReleaseVariant(app, "debug"),
        )
    }

    @Test
    fun `release detection covers flavored release names`() {
        assertTrue(RunTargetResolver.isReleaseVariant("release"))
        assertTrue(RunTargetResolver.isReleaseVariant("developmentRelease"))
        assertFalse(RunTargetResolver.isReleaseVariant("developmentDebug"))
        assertTrue(RunTargetResolver.isDebugVariant("debug"))
        assertTrue(RunTargetResolver.isDebugVariant("developmentDebug"))
        assertFalse(RunTargetResolver.isDebugVariant("developmentRelease"))
    }

    @Test
    fun `returns null when no app module exists`() {
        assertNull(RunTargetResolver.resolveAppModule(listOf(module(":lib", ModuleType.JVM))))
    }
}
