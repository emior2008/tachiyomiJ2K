package eu.kanade.tachiyomi.util.system

import android.graphics.Color
import androidx.core.graphics.ColorUtils

enum class CustomDuskDownloadBadgeStyle(
    val preferenceValue: String,
) {
    LIGHT_ACCENT("light_accent"),
    DARK_ACCENT("dark_accent"),
    LIGHT_CONTRAST("light_contrast"),
    DARK_CONTRAST("dark_contrast"),
    CUSTOM("custom"),
    ;

    companion object {
        fun fromPreference(value: String): CustomDuskDownloadBadgeStyle =
            entries.firstOrNull { it.preferenceValue == value } ?: DARK_CONTRAST
    }
}

data class CustomDuskPalette(
    val primary: Int,
    val primaryInverse: Int,
    val onPrimary: Int,
    val secondary: Int,
    val primaryContainer: Int,
    val secondaryContainer: Int,
    val onPrimaryContainer: Int,
    val tertiary: Int,
    val onTertiary: Int,
    val ripple: Int,
) {
    fun asColorResourceMap(): Map<Int, Int> =
        mapOf(
            eu.kanade.tachiyomi.R.color.custom_dusk_primary to primary,
            eu.kanade.tachiyomi.R.color.custom_dusk_primary_inverse to primaryInverse,
            eu.kanade.tachiyomi.R.color.custom_dusk_on_primary to onPrimary,
            eu.kanade.tachiyomi.R.color.custom_dusk_secondary to secondary,
            eu.kanade.tachiyomi.R.color.custom_dusk_primary_container to primaryContainer,
            eu.kanade.tachiyomi.R.color.custom_dusk_secondary_container to secondaryContainer,
            eu.kanade.tachiyomi.R.color.custom_dusk_on_primary_container to onPrimaryContainer,
            eu.kanade.tachiyomi.R.color.custom_dusk_tertiary to tertiary,
            eu.kanade.tachiyomi.R.color.custom_dusk_on_tertiary to onTertiary,
            eu.kanade.tachiyomi.R.color.custom_dusk_ripple to ripple,
        )

    companion object {
        fun fromSeed(
            seed: Int,
            downloadBadgeStyle: CustomDuskDownloadBadgeStyle = CustomDuskDownloadBadgeStyle.DARK_CONTRAST,
            customDownloadBadgeColor: Int = 0xFF127C52.toInt(),
        ): CustomDuskPalette {
            val opaqueSeed = Color.rgb(Color.red(seed), Color.green(seed), Color.blue(seed))
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(opaqueSeed, hsl)

            val hue = hsl[0]
            val saturation = hsl[1]
            val darkAccent = hslColor(hue, (saturation * 1.03f).coerceAtMost(1f), 0.114f)
            val tertiary =
                when (downloadBadgeStyle) {
                    CustomDuskDownloadBadgeStyle.LIGHT_ACCENT ->
                        hslColor(hue, (saturation * 0.85f).coerceAtLeast(0.45f), 0.72f)
                    CustomDuskDownloadBadgeStyle.DARK_ACCENT ->
                        hslColor(hue, (saturation * 0.85f).coerceAtLeast(0.55f), 0.28f)
                    CustomDuskDownloadBadgeStyle.LIGHT_CONTRAST ->
                        hslColor(hue + 180f, (saturation * 0.80f).coerceAtLeast(0.45f), 0.72f)
                    CustomDuskDownloadBadgeStyle.DARK_CONTRAST ->
                        hslColor(hue + 180f, (saturation * 0.85f).coerceAtLeast(0.55f), 0.28f)
                    CustomDuskDownloadBadgeStyle.CUSTOM ->
                        Color.rgb(
                            Color.red(customDownloadBadgeColor),
                            Color.green(customDownloadBadgeColor),
                            Color.blue(customDownloadBadgeColor),
                        )
                }
            val tertiaryHsl = FloatArray(3)
            ColorUtils.colorToHSL(tertiary, tertiaryHsl)
            val tertiaryDarkAccent =
                hslColor(tertiaryHsl[0], (tertiaryHsl[1] * 1.03f).coerceAtMost(1f), 0.114f)

            return CustomDuskPalette(
                primary = opaqueSeed,
                primaryInverse = hslColor(hue, saturation * 0.967f, 0.30f),
                onPrimary = readableAccentForeground(opaqueSeed, darkAccent),
                secondary = hslColor(hue, (saturation * 1.147f).coerceAtMost(1f), 0.878f),
                primaryContainer = hslColor(hue, saturation * 0.499f, 0.180f),
                secondaryContainer = hslColor(hue - 9.0f, saturation * 0.535f, 0.176f),
                onPrimaryContainer = opaqueSeed,
                tertiary = tertiary,
                onTertiary = readableAccentForeground(tertiary, tertiaryDarkAccent),
                ripple = ColorUtils.setAlphaComponent(opaqueSeed, 0x1F),
            )
        }

        private fun hslColor(
            hue: Float,
            saturation: Float,
            lightness: Float,
        ): Int =
            ColorUtils.HSLToColor(
                floatArrayOf(
                    ((hue % 360f) + 360f) % 360f,
                    saturation.coerceIn(0f, 1f),
                    lightness.coerceIn(0f, 1f),
                ),
            )

        private fun readableAccentForeground(
            background: Int,
            darkAccent: Int,
        ): Int {
            val darkContrast = ColorUtils.calculateContrast(darkAccent, background)
            val whiteContrast = ColorUtils.calculateContrast(Color.WHITE, background)
            return if (darkContrast >= whiteContrast) darkAccent else Color.WHITE
        }
    }
}
