package eu.kanade.tachiyomi.ui.setting

import android.app.Activity
import android.graphics.Color
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.tachiyomi.R

object CustomDuskColorDialog {
    fun show(
        activity: Activity,
        initialColor: Int,
        titleRes: Int = R.string.custom_dusk_accent,
        onApply: (Int) -> Unit,
    ) {
        val density = activity.resources.displayMetrics.density

        fun dp(value: Int) = (value * density).toInt()

        var currentColor = forceOpaque(initialColor)
        var updating = false

        val container =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(8), dp(24), 0)
            }

        val preview =
            View(activity).apply {
                layoutParams =
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)).apply {
                        bottomMargin = dp(12)
                    }
                setBackgroundColor(currentColor)
            }
        container.addView(preview)

        val hexInput =
            EditText(activity).apply {
                hint = activity.getString(R.string.colour_hex)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                filters = arrayOf(InputFilter.LengthFilter(7))
                setSingleLine(true)
            }
        container.addView(hexInput)

        val slidersButton =
            MaterialButton(activity).apply {
                id = View.generateViewId()
                setText(R.string.sliders)
                isCheckable = true
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
        val honeycombButton =
            MaterialButton(activity).apply {
                id = View.generateViewId()
                setText(R.string.honeycomb)
                isCheckable = true
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
        val modeToggle =
            MaterialButtonToggleGroup(activity).apply {
                isSingleSelection = true
                isSelectionRequired = true
                layoutParams =
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = dp(12)
                        bottomMargin = dp(4)
                    }
                addView(slidersButton)
                addView(honeycombButton)
            }
        container.addView(modeToggle)

        val sliderContainer =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
            }
        container.addView(sliderContainer)

        fun addSlider(
            labelRes: Int,
            max: Int,
        ): SeekBar {
            sliderContainer.addView(
                TextView(activity).apply {
                    setText(labelRes)
                    gravity = Gravity.START
                    setPadding(0, dp(12), 0, 0)
                },
            )
            return SeekBar(activity).apply {
                this.max = max
                sliderContainer.addView(this)
            }
        }

        val hueSlider = addSlider(R.string.hue, 360)
        val saturationSlider = addSlider(R.string.saturation, 100)
        val brightnessSlider = addSlider(R.string.brightness, 100)

        val honeycombView =
            CustomDuskHoneycombView(activity).apply {
                visibility = View.GONE
                layoutParams =
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(260)).apply {
                        topMargin = dp(8)
                    }
            }
        container.addView(honeycombView)

        fun formatColor(color: Int): String = String.format("#%06X", forceOpaque(color) and 0xFFFFFF)

        fun updatePreviewAndHex(color: Int) {
            currentColor = forceOpaque(color)
            updating = true
            preview.setBackgroundColor(currentColor)
            hexInput.setText(formatColor(currentColor))
            hexInput.setSelection(hexInput.text.length)
            honeycombView.setSelectedColor(currentColor)
            updating = false
        }

        fun syncAllControls(color: Int) {
            currentColor = forceOpaque(color)
            val hsv = FloatArray(3)
            Color.colorToHSV(currentColor, hsv)
            updating = true
            preview.setBackgroundColor(currentColor)
            hexInput.setText(formatColor(currentColor))
            hexInput.setSelection(hexInput.text.length)
            hueSlider.progress = hsv[0].toInt().coerceIn(0, 360)
            saturationSlider.progress = (hsv[1] * 100f).toInt().coerceIn(0, 100)
            brightnessSlider.progress = (hsv[2] * 100f).toInt().coerceIn(0, 100)
            honeycombView.setSelectedColor(currentColor)
            updating = false
        }

        fun updateFromSliders() {
            if (updating) return
            val color =
                Color.HSVToColor(
                    floatArrayOf(
                        hueSlider.progress.toFloat(),
                        saturationSlider.progress / 100f,
                        brightnessSlider.progress / 100f,
                    ),
                )
            // Do not round-trip through RGB -> HSV here. That used to move the other sliders.
            updatePreviewAndHex(color)
        }

        val sliderListener =
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean,
                ) {
                    if (fromUser) updateFromSliders()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            }
        hueSlider.setOnSeekBarChangeListener(sliderListener)
        saturationSlider.setOnSeekBarChangeListener(sliderListener)
        brightnessSlider.setOnSeekBarChangeListener(sliderListener)

        hexInput.doAfterTextChanged { editable ->
            if (updating) return@doAfterTextChanged
            parseHexColor(editable?.toString())?.let(::syncAllControls)
        }

        honeycombView.onColorSelected = { color ->
            syncAllControls(color)
        }

        modeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val showHoneycomb = checkedId == honeycombButton.id
            sliderContainer.visibility = if (showHoneycomb) View.GONE else View.VISIBLE
            honeycombView.visibility = if (showHoneycomb) View.VISIBLE else View.GONE
            if (showHoneycomb) honeycombView.setSelectedColor(currentColor)
        }

        syncAllControls(currentColor)
        modeToggle.check(slidersButton.id)

        MaterialAlertDialogBuilder(activity)
            .setTitle(titleRes)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.apply) { _, _ -> onApply(currentColor) }
            .show()
    }

    private fun parseHexColor(value: String?): Int? {
        val normalized = value?.trim().orEmpty().removePrefix("#")
        if (!normalized.matches(Regex("^[0-9A-Fa-f]{6}$"))) return null
        return runCatching { forceOpaque(Color.parseColor("#$normalized")) }.getOrNull()
    }

    private fun forceOpaque(color: Int): Int = Color.rgb(Color.red(color), Color.green(color), Color.blue(color))
}
