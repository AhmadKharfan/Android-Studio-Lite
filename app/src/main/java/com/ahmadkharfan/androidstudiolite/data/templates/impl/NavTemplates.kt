package com.ahmadkharfan.androidstudiolite.data.templates.impl

import com.ahmadkharfan.androidstudiolite.data.templates.Catalog
import com.ahmadkharfan.androidstudiolite.data.templates.ProjectRecipe
import com.ahmadkharfan.androidstudiolite.data.templates.Template
import com.ahmadkharfan.androidstudiolite.data.templates.TemplateContent
import com.ahmadkharfan.androidstudiolite.data.templates.TemplateMetadata
import com.ahmadkharfan.androidstudiolite.domain.model.NewProjectSpec

/** Shared helpers for the Views + Navigation-component templates (Kotlin-only). */
internal object NavSupport {

    fun applyBase(recipe: ProjectRecipe) {
        recipe.plugin(Catalog.androidApplication)
        recipe.plugin(Catalog.kotlinAndroid)
        recipe.enableViewBinding = true
        recipe.implementation(Catalog.coreKtx)
        recipe.implementation(Catalog.appcompat)
        recipe.implementation(Catalog.material)
        recipe.implementation(Catalog.constraintLayout)
        recipe.implementation(Catalog.lifecycleViewModel)
        recipe.implementation(Catalog.lifecycleLiveData)
        recipe.implementation(Catalog.navigationFragment)
        recipe.implementation(Catalog.navigationUi)
        TemplateContent.addStandardTestDeps(recipe)
    }

    /** A minimal Fragment named [className] that inflates a centered-TextView layout [layout]. */
    fun placeholderFragment(spec: NewProjectSpec, recipe: ProjectRecipe, className: String, layout: String, label: String) {
        val pkg = spec.packageName
        recipe.sourceFileIn(
            "ui", "$className.kt",
            """
            package $pkg.ui

            import android.os.Bundle
            import android.view.LayoutInflater
            import android.view.View
            import android.view.ViewGroup
            import androidx.fragment.app.Fragment
            import $pkg.R

            class $className : Fragment() {
                override fun onCreateView(
                    inflater: LayoutInflater,
                    container: ViewGroup?,
                    savedInstanceState: Bundle?,
                ): View = inflater.inflate(R.layout.$layout, container, false)
            }
            """.trimIndent(),
        )
        recipe.file("app/src/main/res/layout/$layout.xml", centeredTextLayout(label))
    }

    fun centeredTextLayout(text: String): String =
        """
        <?xml version="1.0" encoding="utf-8"?>
        <androidx.constraintlayout.widget.ConstraintLayout
            xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto"
            android:layout_width="match_parent"
            android:layout_height="match_parent">

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="$text"
                android:textAppearance="?attr/textAppearanceHeadlineSmall"
                app:layout_constraintBottom_toBottomOf="parent"
                app:layout_constraintEnd_toEndOf="parent"
                app:layout_constraintStart_toStartOf="parent"
                app:layout_constraintTop_toTopOf="parent" />

        </androidx.constraintlayout.widget.ConstraintLayout>
        """.trimIndent()
}

/** Bottom navigation bar hosting three destinations via the Navigation component. */
object BottomNavigationTemplate : Template {

    override val metadata = TemplateMetadata(
        id = "bottom-nav",
        name = "Bottom Navigation",
        description = "Three destinations behind a bottom navigation bar.",
        icon = "layout-grid",
        tags = listOf("Kotlin", "Views", "Navigation"),
    )

    override val supportsJava = false

    override fun assemble(spec: NewProjectSpec, recipe: ProjectRecipe) {
        NavSupport.applyBase(recipe)
        val pkg = spec.packageName
        recipe.file("app/src/main/AndroidManifest.xml", TemplateContent.manifest(spec, "MainActivity"))
        ViewsSupport.commonResources(spec, recipe)

        recipe.sourceFile(
            "MainActivity.kt",
            """
            package $pkg

            import android.os.Bundle
            import androidx.appcompat.app.AppCompatActivity
            import androidx.navigation.fragment.NavHostFragment
            import androidx.navigation.ui.setupWithNavController
            import com.google.android.material.bottomnavigation.BottomNavigationView

            class MainActivity : AppCompatActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    setContentView(R.layout.activity_main)
                    val navView = findViewById<BottomNavigationView>(R.id.nav_view)
                    // The host is a FragmentContainerView, so its NavController must come from the
                    // fragment itself: Activity.findNavController() reads a tag set on the fragment's
                    // view, which doesn't exist yet in onCreate, and throws "does not have a
                    // NavController set".
                    val navHostFragment = supportFragmentManager
                        .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
                    navView.setupWithNavController(navHostFragment.navController)
                }
            }
            """.trimIndent(),
        )

        NavSupport.placeholderFragment(spec, recipe, "HomeFragment", "fragment_home", "Home")
        NavSupport.placeholderFragment(spec, recipe, "DashboardFragment", "fragment_dashboard", "Dashboard")
        NavSupport.placeholderFragment(spec, recipe, "NotificationsFragment", "fragment_notifications", "Notifications")

        recipe.file(
            "app/src/main/res/layout/activity_main.xml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <androidx.constraintlayout.widget.ConstraintLayout
                xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:app="http://schemas.android.com/apk/res-auto"
                android:layout_width="match_parent"
                android:layout_height="match_parent">

                <com.google.android.material.bottomnavigation.BottomNavigationView
                    android:id="@+id/nav_view"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    app:layout_constraintBottom_toBottomOf="parent"
                    app:layout_constraintEnd_toEndOf="parent"
                    app:layout_constraintStart_toStartOf="parent"
                    app:menu="@menu/bottom_nav_menu" />

                <androidx.fragment.app.FragmentContainerView
                    android:id="@+id/nav_host_fragment"
                    android:name="androidx.navigation.fragment.NavHostFragment"
                    android:layout_width="match_parent"
                    android:layout_height="0dp"
                    app:defaultNavHost="true"
                    app:layout_constraintBottom_toTopOf="@id/nav_view"
                    app:layout_constraintTop_toTopOf="parent"
                    app:navGraph="@navigation/mobile_navigation" />

            </androidx.constraintlayout.widget.ConstraintLayout>
            """.trimIndent(),
        )

        recipe.file(
            "app/src/main/res/menu/bottom_nav_menu.xml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <menu xmlns:android="http://schemas.android.com/apk/res/android">
                <item android:id="@+id/navigation_home" android:title="Home" />
                <item android:id="@+id/navigation_dashboard" android:title="Dashboard" />
                <item android:id="@+id/navigation_notifications" android:title="Notifications" />
            </menu>
            """.trimIndent(),
        )

        recipe.file(
            "app/src/main/res/navigation/mobile_navigation.xml",
            navGraph(
                pkg,
                startDest = "navigation_home",
                destinations = listOf(
                    Triple("navigation_home", "HomeFragment", "Home"),
                    Triple("navigation_dashboard", "DashboardFragment", "Dashboard"),
                    Triple("navigation_notifications", "NotificationsFragment", "Notifications"),
                ),
                fragmentLayouts = mapOf(
                    "HomeFragment" to "fragment_home",
                    "DashboardFragment" to "fragment_dashboard",
                    "NotificationsFragment" to "fragment_notifications",
                ),
            ),
        )
    }
}

/** Swipeable tabs backed by ViewPager2 + TabLayout. */
object TabbedTemplate : Template {

    override val metadata = TemplateMetadata(
        id = "tabbed",
        name = "Tabbed Activity",
        description = "Swipeable tabs using ViewPager2 and a TabLayout.",
        icon = "table-columns",
        tags = listOf("Kotlin", "Views"),
    )

    override val supportsJava = false

    override fun assemble(spec: NewProjectSpec, recipe: ProjectRecipe) {
        NavSupport.applyBase(recipe)
        recipe.implementation(Catalog.viewpager2)
        val pkg = spec.packageName
        recipe.file("app/src/main/AndroidManifest.xml", TemplateContent.manifest(spec, "MainActivity"))
        ViewsSupport.commonResources(spec, recipe)

        recipe.sourceFile(
            "MainActivity.kt",
            """
            package $pkg

            import android.os.Bundle
            import androidx.appcompat.app.AppCompatActivity
            import androidx.fragment.app.Fragment
            import androidx.fragment.app.FragmentActivity
            import androidx.viewpager2.adapter.FragmentStateAdapter
            import androidx.viewpager2.widget.ViewPager2
            import com.google.android.material.tabs.TabLayout
            import com.google.android.material.tabs.TabLayoutMediator
            import $pkg.ui.PlaceholderFragment

            class MainActivity : AppCompatActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    setContentView(R.layout.activity_main)
                    val pager = findViewById<ViewPager2>(R.id.view_pager)
                    val tabs = findViewById<TabLayout>(R.id.tabs)
                    pager.adapter = TabAdapter(this)
                    TabLayoutMediator(tabs, pager) { tab, position ->
                        tab.text = "Tab ${'$'}{position + 1}"
                    }.attach()
                }
            }

            private class TabAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
                override fun getItemCount(): Int = 3
                override fun createFragment(position: Int): Fragment =
                    PlaceholderFragment.newInstance(position + 1)
            }
            """.trimIndent(),
        )

        recipe.sourceFileIn(
            "ui", "PlaceholderFragment.kt",
            """
            package $pkg.ui

            import android.os.Bundle
            import android.view.LayoutInflater
            import android.view.View
            import android.view.ViewGroup
            import android.widget.TextView
            import androidx.fragment.app.Fragment
            import $pkg.R

            class PlaceholderFragment : Fragment() {
                override fun onCreateView(
                    inflater: LayoutInflater,
                    container: ViewGroup?,
                    savedInstanceState: Bundle?,
                ): View {
                    val root = inflater.inflate(R.layout.fragment_tab, container, false)
                    val index = arguments?.getInt(ARG_SECTION_NUMBER) ?: 1
                    root.findViewById<TextView>(R.id.section_label).text = "Tab ${'$'}index"
                    return root
                }

                companion object {
                    private const val ARG_SECTION_NUMBER = "section_number"

                    fun newInstance(sectionNumber: Int): PlaceholderFragment =
                        PlaceholderFragment().apply {
                            arguments = Bundle().apply { putInt(ARG_SECTION_NUMBER, sectionNumber) }
                        }
                }
            }
            """.trimIndent(),
        )

        recipe.file(
            "app/src/main/res/layout/fragment_tab.xml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <androidx.constraintlayout.widget.ConstraintLayout
                xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:app="http://schemas.android.com/apk/res-auto"
                android:layout_width="match_parent"
                android:layout_height="match_parent">

                <TextView
                    android:id="@+id/section_label"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    app:layout_constraintBottom_toBottomOf="parent"
                    app:layout_constraintEnd_toEndOf="parent"
                    app:layout_constraintStart_toStartOf="parent"
                    app:layout_constraintTop_toTopOf="parent" />

            </androidx.constraintlayout.widget.ConstraintLayout>
            """.trimIndent(),
        )

        recipe.file(
            "app/src/main/res/layout/activity_main.xml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:orientation="vertical">

                <com.google.android.material.tabs.TabLayout
                    android:id="@+id/tabs"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content" />

                <androidx.viewpager2.widget.ViewPager2
                    android:id="@+id/view_pager"
                    android:layout_width="match_parent"
                    android:layout_height="0dp"
                    android:layout_weight="1" />

            </LinearLayout>
            """.trimIndent(),
        )
    }
}

/** A navigation drawer with three destinations via DrawerLayout + NavigationView. */
object NavDrawerTemplate : Template {

    override val metadata = TemplateMetadata(
        id = "nav-drawer",
        name = "Navigation Drawer",
        description = "A side drawer with three destinations via the Navigation component.",
        icon = "panel-left",
        tags = listOf("Kotlin", "Views", "Navigation"),
    )

    override val supportsJava = false

    override fun assemble(spec: NewProjectSpec, recipe: ProjectRecipe) {
        NavSupport.applyBase(recipe)
        val pkg = spec.packageName
        recipe.file("app/src/main/AndroidManifest.xml", TemplateContent.manifest(spec, "MainActivity"))
        ViewsSupport.commonResources(spec, recipe)

        recipe.sourceFile(
            "MainActivity.kt",
            """
            package $pkg

            import android.os.Bundle
            import androidx.appcompat.app.AppCompatActivity
            import androidx.appcompat.widget.Toolbar
            import androidx.drawerlayout.widget.DrawerLayout
            import androidx.navigation.NavController
            import androidx.navigation.fragment.NavHostFragment
            import androidx.navigation.ui.AppBarConfiguration
            import androidx.navigation.ui.navigateUp
            import androidx.navigation.ui.setupActionBarWithNavController
            import androidx.navigation.ui.setupWithNavController
            import com.google.android.material.navigation.NavigationView

            class MainActivity : AppCompatActivity() {
                private lateinit var appBarConfiguration: AppBarConfiguration
                private lateinit var navController: NavController

                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    setContentView(R.layout.activity_main)
                    setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))

                    val drawerLayout = findViewById<DrawerLayout>(R.id.drawer_layout)
                    val navView = findViewById<NavigationView>(R.id.nav_view)
                    // The host is a FragmentContainerView, so its NavController must come from the
                    // fragment itself: Activity.findNavController() reads a tag set on the fragment's
                    // view, which doesn't exist yet in onCreate, and throws "does not have a
                    // NavController set".
                    val navHostFragment = supportFragmentManager
                        .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
                    navController = navHostFragment.navController

                    // Passing the drawer here is what turns the action bar's Up arrow into the
                    // hamburger that opens it.
                    appBarConfiguration = AppBarConfiguration(
                        setOf(R.id.nav_home, R.id.nav_dashboard, R.id.nav_notifications),
                        drawerLayout,
                    )
                    setupActionBarWithNavController(navController, appBarConfiguration)
                    navView.setupWithNavController(navController)
                }

                override fun onSupportNavigateUp(): Boolean =
                    navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
            }
            """.trimIndent(),
        )

        NavSupport.placeholderFragment(spec, recipe, "HomeFragment", "fragment_home", "Home")
        NavSupport.placeholderFragment(spec, recipe, "DashboardFragment", "fragment_dashboard", "Dashboard")
        NavSupport.placeholderFragment(spec, recipe, "NotificationsFragment", "fragment_notifications", "Notifications")

        recipe.file(
            "app/src/main/res/layout/activity_main.xml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <androidx.drawerlayout.widget.DrawerLayout
                xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:app="http://schemas.android.com/apk/res-auto"
                android:id="@+id/drawer_layout"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:fitsSystemWindows="true">

                <!-- DrawerLayout's first child is the content; the drawer itself follows it. -->
                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:orientation="vertical">

                    <androidx.appcompat.widget.Toolbar
                        android:id="@+id/toolbar"
                        android:layout_width="match_parent"
                        android:layout_height="?attr/actionBarSize"
                        android:background="?attr/colorPrimary"
                        app:titleTextColor="?attr/colorOnPrimary" />

                    <androidx.fragment.app.FragmentContainerView
                        android:id="@+id/nav_host_fragment"
                        android:name="androidx.navigation.fragment.NavHostFragment"
                        android:layout_width="match_parent"
                        android:layout_height="match_parent"
                        app:defaultNavHost="true"
                        app:navGraph="@navigation/mobile_navigation" />

                </LinearLayout>

                <com.google.android.material.navigation.NavigationView
                    android:id="@+id/nav_view"
                    android:layout_width="wrap_content"
                    android:layout_height="match_parent"
                    android:layout_gravity="start"
                    app:menu="@menu/activity_main_drawer" />

            </androidx.drawerlayout.widget.DrawerLayout>
            """.trimIndent(),
        )

        recipe.file(
            "app/src/main/res/menu/activity_main_drawer.xml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <menu xmlns:android="http://schemas.android.com/apk/res/android">
                <group android:checkableBehavior="single">
                    <item android:id="@+id/nav_home" android:title="Home" />
                    <item android:id="@+id/nav_dashboard" android:title="Dashboard" />
                    <item android:id="@+id/nav_notifications" android:title="Notifications" />
                </group>
            </menu>
            """.trimIndent(),
        )

        recipe.file(
            "app/src/main/res/navigation/mobile_navigation.xml",
            navGraph(
                pkg,
                startDest = "nav_home",
                destinations = listOf(
                    Triple("nav_home", "HomeFragment", "Home"),
                    Triple("nav_dashboard", "DashboardFragment", "Dashboard"),
                    Triple("nav_notifications", "NotificationsFragment", "Notifications"),
                ),
                fragmentLayouts = mapOf(
                    "HomeFragment" to "fragment_home",
                    "DashboardFragment" to "fragment_dashboard",
                    "NotificationsFragment" to "fragment_notifications",
                ),
            ),
        )
    }
}

/** Builds a Navigation-component graph XML with the given destinations. [destinations] = (id, fragment, label). */
private fun navGraph(
    pkg: String,
    startDest: String,
    destinations: List<Triple<String, String, String>>,
    fragmentLayouts: Map<String, String>,
): String = buildString {
    appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
    appendLine("<navigation xmlns:android=\"http://schemas.android.com/apk/res/android\"")
    appendLine("    xmlns:app=\"http://schemas.android.com/apk/res-auto\"")
    appendLine("    xmlns:tools=\"http://schemas.android.com/tools\"")
    appendLine("    android:id=\"@+id/mobile_navigation\"")
    appendLine("    app:startDestination=\"@+id/$startDest\">")
    appendLine()
    for ((id, fragment, label) in destinations) {
        val layout = fragmentLayouts[fragment]
        appendLine("    <fragment")
        appendLine("        android:id=\"@+id/$id\"")
        appendLine("        android:name=\"$pkg.ui.$fragment\"")
        appendLine("        android:label=\"$label\"")
        appendLine("        tools:layout=\"@layout/$layout\" />")
        appendLine()
    }
    append("</navigation>")
}
