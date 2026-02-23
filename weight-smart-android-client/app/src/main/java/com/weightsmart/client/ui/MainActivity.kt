package com.weightsmart.client.ui

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.weightsmart.client.ui.profile.ProfileActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.weightsmart.client.R
import dagger.hilt.android.AndroidEntryPoint

/**
 * MainActivity
 * The shell Activity that hosts the bottom navigation and the NavHostFragment for all main screens.
 *
 * Architecture Role:
 * Single-Activity architecture host. After successful login, [LoginActivity] or [RegistrationActivity]
 * launches this Activity with FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK (clearing the auth
 * back stack). Contains no business logic -- it only wires navigation components.
 *
 * Lifecycle / Navigation:
 * onCreate -> setContentView -> wire NavHostFragment to BottomNavigationView ->
 *   wire profile ImageButton to launch [ProfileActivity] via explicit Intent.
 * The NavHostFragment hosts: HomeFragment, TableFragment, AnalyticsFragment (defined in nav_graph.xml).
 * Bottom nav reselection is suppressed (no-op) to avoid reloading the current fragment.
 *
 * Key Concepts & Documentation:
 * Navigation Component: Jetpack library for fragment navigation via a nav graph.
 * <a href="https://developer.android.com/guide/navigation">Reference: Navigation Component</a>
 * BottomNavigationView.setupWithNavController: Binds bottom nav items to nav graph destinations.
 * <a href="https://developer.android.com/reference/androidx/navigation/ui/NavigationUI#setupWithNavController(com.google.android.material.bottomnavigation.BottomNavigationView,androidx.navigation.NavController)">Reference: setupWithNavController</a>
 *
 * @author James Chase
 * @version 1.1 (Inlined profile button — replaced AccountButtonOverlay)
 * @since 2026-01-20
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // --- NavHost / BottomNav Wiring ---
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setupWithNavController(navController)
        // Suppress reselection to avoid reloading the current fragment on double-tap
        bottomNav.setOnItemReselectedListener { /* no-op */ }

        // --- Profile Button ---
        findViewById<ImageButton>(R.id.profile_button).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }
}
