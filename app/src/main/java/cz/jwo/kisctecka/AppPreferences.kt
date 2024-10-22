package cz.jwo.kisctecka

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

class AppPreferences(private val context: Context, private val sharedPreferences: SharedPreferences) {
    constructor(context: Context) : this(context, PreferenceManager.getDefaultSharedPreferences(context))

    val flashOnRead get() = sharedPreferences.getBoolean(PREFERENCE_FLASH_ON_READ, false)
    val flashBrightness get() = sharedPreferences.getInt(PREFERENCE_FLASH_BRIGHTNESS, 65535)
    val flashDuration
        get() = sharedPreferences.getInt(
            PREFERENCE_FLASH_DURATION,
            context.resources.getInteger(R.integer.default_flash_duration)
        )
    val flashOnlyOnSuccess get() = sharedPreferences.getBoolean(PREFERENCE_FLASH_ONLY_ON_SUCCESS, false)
    val keepScreenOn get() = sharedPreferences.getBoolean(PREFERENCE_KEEP_SCREEN_ON, false)
    val useBlackTheme get() = sharedPreferences.getBoolean(PREFERENCE_BLACK_THEME, false)
    val useProximitySensor get() = sharedPreferences.getBoolean(PREFERENCE_USE_PROXIMITY_SENSOR, false)

    val showRepeatButton get() = sharedPreferences.getBoolean(PREFERENCE_SHOW_REPEAT_BUTTON, false)
    val repeatOnVolumeUp get() = sharedPreferences.getBoolean(PREFERENCE_REPEAT_ON_VOLUME_UP, false)
    val repeatOnVolumeDown get() = sharedPreferences.getBoolean(PREFERENCE_REPEAT_ON_VOLUME_DOWN, false)
    private val repeatCardModeRaw get() = sharedPreferences.getString(PREFERENCE_REPEAT_CARD_MODE, defaultRepeatCardMode)!!
    val repeatCardMode get() = parseRepeatCardMode(context, repeatCardModeRaw)

    private val defaultRepeatCardMode = context.resources.getStringArray(R.array.settings_repeat_mode_values).first()

    companion object {
        const val PREFERENCE_FLASH_ON_READ = "flash_on_read"
        const val PREFERENCE_FLASH_BRIGHTNESS = "flash_brightness"
        const val PREFERENCE_FLASH_DURATION = "flash_duration"
        const val PREFERENCE_FLASH_ONLY_ON_SUCCESS = "flash_only_on_success"
        const val PREFERENCE_KEEP_SCREEN_ON = "keep_screen_on"
        const val PREFERENCE_BLACK_THEME = "black_theme"
        const val PREFERENCE_USE_PROXIMITY_SENSOR = "use_proximity_sensor"
        const val PREFERENCE_SHOW_REPEAT_BUTTON = "show_repeat_button"
        const val PREFERENCE_REPEAT_ON_VOLUME_UP = "repeat_on_volume_up"
        const val PREFERENCE_REPEAT_ON_VOLUME_DOWN = "repeat_on_volume_down"
        const val PREFERENCE_REPEAT_CARD_MODE = "repeat_card_mode"

        enum class RepeatCardMode {
            Last,
            First
        }

        fun parseRepeatCardMode(context: Context, modeName: String): RepeatCardMode {
            return context.resources.getStringArray(R.array.settings_repeat_mode_values)
                .zip(RepeatCardMode.entries)
                .toMap()
                .get(modeName)
                ?: throw IllegalArgumentException("Invalid card repeat mode")
        }
    }
}