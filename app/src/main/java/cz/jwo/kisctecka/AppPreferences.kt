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

    companion object {
        const val PREFERENCE_FLASH_ON_READ = "flash_on_read"
        const val PREFERENCE_FLASH_BRIGHTNESS = "flash_brightness"
        const val PREFERENCE_FLASH_DURATION = "flash_duration"
        const val PREFERENCE_FLASH_ONLY_ON_SUCCESS = "flash_only_on_success"
        const val PREFERENCE_KEEP_SCREEN_ON = "keep_screen_on"
        const val PREFERENCE_BLACK_THEME = "black_theme"
        const val PREFERENCE_USE_PROXIMITY_SENSOR = "use_proximity_sensor"
    }
}