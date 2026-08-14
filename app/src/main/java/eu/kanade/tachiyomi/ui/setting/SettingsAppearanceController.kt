package eu.kanade.tachiyomi.ui.setting

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.doOnNextLayout
import androidx.core.view.isVisible
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.asImmediateFlow
import eu.kanade.tachiyomi.data.preference.asImmediateFlowIn
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.CustomDuskDownloadBadgeStyle
import eu.kanade.tachiyomi.util.system.SideNavMode
import eu.kanade.tachiyomi.util.system.Themes
import eu.kanade.tachiyomi.util.system.appDelegateNightMode
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getPrefTheme
import eu.kanade.tachiyomi.util.system.isInNightMode
import eu.kanade.tachiyomi.util.view.activityBinding
import eu.kanade.tachiyomi.util.view.moveRecyclerViewUp
import kotlinx.coroutines.flow.launchIn
import kotlin.math.max
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys

class SettingsAppearanceController : SettingsController() {
    var lastThemeXLight: Int? = null
    var lastThemeXDark: Int? = null
    var themePreference: ThemePreference? = null

    @SuppressLint("NotifyDataSetChanged")
    override fun setupPreferenceScreen(screen: PreferenceScreen) =
        screen.apply {
            titleRes = R.string.appearance

            preferenceCategory {
                titleRes = R.string.app_theme

                themePreference =
                    themePreference {
                        key = "theme_preference"
                        titleRes = R.string.app_theme
                        lastScrollPostionLight = lastThemeXLight
                        lastScrollPostionDark = lastThemeXDark
                        summary = context.getString(context.getPrefTheme(preferences).nameRes)
                        activity = this@SettingsAppearanceController.activity
                    }

                preference {
                    key = "custom_dusk_accent_picker"
                    titleRes = R.string.custom_dusk_accent
                    summary = formatCustomDuskColor(preferences.customDuskAccentColor().get())
                    isVisible =
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                        preferences.darkTheme().get() == Themes.CUSTOM_DUSK

                    preferences.darkTheme().asImmediateFlowIn(viewScope) { darkTheme ->
                        isVisible =
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                            darkTheme == Themes.CUSTOM_DUSK
                    }

                    onClick {
                        val hostActivity = activity ?: return@onClick
                        CustomDuskColorDialog.show(
                            hostActivity,
                            preferences.customDuskAccentColor().get(),
                        ) { color ->
                            preferences.customDuskAccentColor().set(color)
                            summary = formatCustomDuskColor(color)
                            themePreference?.fastAdapterDark?.notifyDataSetChanged()
                            if (context.getPrefTheme(preferences) == Themes.CUSTOM_DUSK) {
                                (activity as? MainActivity)?.recreateFully() ?: activity?.recreate()
                            }
                        }
                    }
                }

                listPreference(activity) {
                    key = Keys.customDuskDownloadBadgeStyle
                    titleRes = R.string.custom_dusk_download_badge_colour
                    val styles = CustomDuskDownloadBadgeStyle.entries
                    entriesRes =
                        arrayOf(
                            R.string.light_accent,
                            R.string.dark_accent,
                            R.string.light_contrast,
                            R.string.dark_contrast,
                            R.string.custom,
                        )
                    entryValues = styles.map { it.preferenceValue }
                    defaultValue = CustomDuskDownloadBadgeStyle.DARK_CONTRAST.preferenceValue
                    isVisible =
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                        preferences.darkTheme().get() == Themes.CUSTOM_DUSK

                    preferences.darkTheme().asImmediateFlowIn(viewScope) { darkTheme ->
                        isVisible =
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                            darkTheme == Themes.CUSTOM_DUSK
                    }

                    onChange {
                        if (context.getPrefTheme(preferences) == Themes.CUSTOM_DUSK) {
                            (activity as? MainActivity)?.recreateFully() ?: activity?.recreate()
                        }
                        true
                    }
                }

                preference {
                    key = "custom_dusk_download_badge_custom_picker"
                    titleRes = R.string.custom_dusk_custom_download_badge_colour
                    summary = formatCustomDuskColor(preferences.customDuskDownloadBadgeColor().get())

                    fun updateVisibility() {
                        isVisible =
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                            preferences.darkTheme().get() == Themes.CUSTOM_DUSK &&
                            CustomDuskDownloadBadgeStyle.fromPreference(
                                preferences.customDuskDownloadBadgeStyle().get(),
                            ) == CustomDuskDownloadBadgeStyle.CUSTOM
                    }

                    updateVisibility()
                    preferences.darkTheme().asImmediateFlowIn(viewScope) { updateVisibility() }
                    preferences.customDuskDownloadBadgeStyle().asImmediateFlowIn(viewScope) { updateVisibility() }

                    onClick {
                        val hostActivity = activity ?: return@onClick
                        CustomDuskColorDialog.show(
                            hostActivity,
                            preferences.customDuskDownloadBadgeColor().get(),
                            R.string.custom_dusk_custom_download_badge_colour,
                        ) { color ->
                            preferences.customDuskDownloadBadgeColor().set(color)
                            summary = formatCustomDuskColor(color)
                            if (context.getPrefTheme(preferences) == Themes.CUSTOM_DUSK) {
                                (activity as? MainActivity)?.recreateFully() ?: activity?.recreate()
                            }
                        }
                    }
                }

                switchPreference {
                    key = "night_mode_switch"
                    isPersistent = false
                    titleRes = R.string.follow_system_theme
                    isChecked =
                        preferences.nightMode().get() == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM

                    onChange {
                        if (it == true) {
                            preferences.nightMode().set(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                            (activity as? MainActivity)?.recreateFully() ?: activity?.recreate()
                        } else {
                            preferences.nightMode().set(context.appDelegateNightMode())
                            themePreference?.fastAdapterLight?.notifyDataSetChanged()
                            themePreference?.fastAdapterDark?.notifyDataSetChanged()
                        }
                        true
                    }
                    preferences
                        .nightMode()
                        .asImmediateFlow { mode ->
                            isChecked = mode == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                        }.launchIn(viewScope)
                }

                switchPreference {
                    key = Keys.themeDarkAmoled
                    titleRes = R.string.pure_black_dark_mode
                    defaultValue = false

                    preferences.nightMode().asImmediateFlowIn(viewScope) { mode ->
                        isVisible = mode != AppCompatDelegate.MODE_NIGHT_NO
                    }

                    onChange {
                        if (context.isInNightMode()) {
                            (activity as? MainActivity)?.recreateFully() ?: activity?.recreate()
                        } else {
                            themePreference?.fastAdapterDark?.notifyDataSetChanged()
                        }
                        true
                    }
                }
            }

            preferenceCategory {
                switchPreference {
                    bindTo(preferences.useLargeToolbar())
                    titleRes = R.string.expanded_toolbar
                    summaryRes = R.string.show_larger_toolbar

                    onChange {
                        val useLarge = it as Boolean
                        activityBinding?.appBar?.setToolbarModeBy(this@SettingsAppearanceController, !useLarge)
                        activityBinding?.appBar?.hideBigView(!useLarge, !useLarge)
                        activityBinding?.toolbar?.alpha = 1f
                        activityBinding?.toolbar?.translationY = 0f
                        activityBinding?.toolbar?.isVisible = true
                        activityBinding?.appBar?.doOnNextLayout {
                            listView.requestApplyInsets()
                            listView.post {
                                if (useLarge) {
                                    moveRecyclerViewUp(true)
                                } else {
                                    activityBinding?.appBar?.updateAppBarAfterY(listView)
                                }
                            }
                        }
                        true
                    }
                }
            }

            preferenceCategory {
                titleRes = R.string.details_page
                switchPreference {
                    key = Keys.themeMangaDetails
                    titleRes = R.string.theme_buttons_based_on_cover
                    defaultValue = true
                }
                switchPreference {
                    key = Keys.renderDescriptionImages
                    titleRes = R.string.render_description_images
                    summaryRes = R.string.render_description_images_summary
                    defaultValue = true
                }
                switchPreference {
                    key = Keys.showChapterMissingWarnings
                    titleRes = R.string.show_missing_chapters
                    summaryRes = R.string.show_missing_chapters_summary
                    defaultValue = true
                }
            }

            preferenceCategory {
                titleRes = R.string.navigation

                switchPreference {
                    key = Keys.hideBottomNavOnScroll
                    titleRes = R.string.hide_bottom_nav
                    summaryRes = R.string.hides_on_scroll
                    defaultValue = true
                }

                intListPreference(activity) {
                    key = Keys.sideNavIconAlignment
                    titleRes = R.string.side_nav_icon_alignment
                    entriesRes = arrayOf(R.string.top, R.string.center, R.string.bottom)
                    entryRange = 0..2
                    defaultValue = 1
                    isVisible = max(
                        context.resources.displayMetrics.widthPixels,
                        context.resources.displayMetrics.heightPixels,
                    ) >= 720.dpToPx
                }

                intListPreference(activity) {
                    key = Keys.sideNavMode
                    titleRes = R.string.use_side_navigation
                    val values = SideNavMode.entries
                    entriesRes = values.map { it.stringRes }.toTypedArray()
                    entryValues = values.map { it.prefValue }
                    defaultValue = SideNavMode.DEFAULT.prefValue

                    onChange {
                        (activity as? MainActivity)?.recreateFully() ?: activity?.recreate()
                        true
                    }
                }

                infoPreference(R.string.by_default_side_nav_info)
            }
        }

    private fun formatCustomDuskColor(color: Int): String =
        String.format(
            "#%06X",
            Color.rgb(Color.red(color), Color.green(color), Color.blue(color)) and 0xFFFFFF,
        )

    override fun onDestroyView(view: View) {
        super.onDestroyView(view)
        themePreference = null
    }

    override fun onSaveViewState(
        view: View,
        outState: Bundle,
    ) {
        outState.putInt(::lastThemeXLight.name, themePreference?.lastScrollPostionLight ?: 0)
        outState.putInt(::lastThemeXDark.name, themePreference?.lastScrollPostionDark ?: 0)
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreViewState(
        view: View,
        savedViewState: Bundle,
    ) {
        super.onRestoreViewState(view, savedViewState)
        lastThemeXLight = savedViewState.getInt(::lastThemeXLight.name)
        lastThemeXDark = savedViewState.getInt(::lastThemeXDark.name)
        themePreference?.lastScrollPostionLight = lastThemeXLight
        themePreference?.lastScrollPostionDark = lastThemeXDark
    }
}
