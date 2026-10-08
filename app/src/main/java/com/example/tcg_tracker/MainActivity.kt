package com.example.tcg_tracker

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import com.example.tcg_tracker.data.MockRepository
import com.example.tcg_tracker.databinding.ActivityMainBinding
import com.example.tcg_tracker.util.BackgroundMusicManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize repository persistence
        MockRepository.init(this)
        BackgroundMusicManager.init(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_collection,
                R.id.navigation_expansions,
                R.id.navigation_search,
                R.id.navigation_wishlist,
                R.id.navigation_lists
            )
        )

        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration)
        NavigationUI.setupWithNavController(binding.bottomNavigation, navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            // Hide keyboard on menu change or navigation
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            currentFocus?.let { view ->
                imm.hideSoftInputFromWindow(view.windowToken, 0)
                view.clearFocus()
            }

            when (destination.id) {
                R.id.navigation_detail, R.id.navigation_add_card, R.id.navigation_expansion_detail, R.id.navigation_folder_detail -> {
                    binding.bottomNavigation.visibility = View.GONE
                }
                else -> {
                    binding.bottomNavigation.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        BackgroundMusicManager.play(this)
    }

    override fun onPause() {
        super.onPause()
        BackgroundMusicManager.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        BackgroundMusicManager.stop()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_settings) {
            showSettingsDialog()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showSettingsDialog() {
        val options = arrayOf(
            getString(R.string.language_dialog_title),
            if (BackgroundMusicManager.isMuted()) getString(R.string.sound_unmuted) else getString(R.string.settings_sound)
        )
        AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
            .setTitle(R.string.settings_title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showLanguageDialog()
                    1 -> {
                        val muted = BackgroundMusicManager.toggleMute(this)
                        val msg = if (muted) getString(R.string.sound_muted) else getString(R.string.sound_unmuted)
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.btn_close, null)
            .show()
    }

    private fun showLanguageDialog() {
        val languages = arrayOf(getString(R.string.language_spanish), getString(R.string.language_english))
        val current = if (AppCompatDelegate.getApplicationLocales().toLanguageTags().startsWith("en")) 1 else 0
        AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
            .setTitle(R.string.language_dialog_title)
            .setSingleChoiceItems(languages, current) { dialog, which ->
                val tag = if (which == 1) "en" else "es"
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(tag)
                )
                dialog.dismiss()
                recreate()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        return navHostFragment.navController.navigateUp() || super.onSupportNavigateUp()
    }
}
