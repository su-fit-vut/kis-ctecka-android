package cz.jwo.kisctecka

import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.TwoStatePreference

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    class SettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {
        private lateinit var flashEnabledPref: TwoStatePreference
        private lateinit var flashOnlyOnSuccess: TwoStatePreference
        private lateinit var flashBrightnessPref: SeekBarPreference

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)

            flashBrightnessPref = findPreference<SeekBarPreference>("flash_brightness")!!
            flashEnabledPref = findPreference<TwoStatePreference>("flash_on_read")!!
            flashOnlyOnSuccess = findPreference<TwoStatePreference>("flash_only_on_success")!!

            flashBrightnessPref.isVisible = MainActivity.getTorchBrightnessRegulationAvailable(requireContext())

            refreshPreferenceDependencies()

            preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener (this)
        }

        private fun refreshPreferenceDependencies() {
            flashEnabledPref.isChecked.let {
                flashBrightnessPref.isEnabled = it
                flashOnlyOnSuccess.isEnabled = it
            }
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            refreshPreferenceDependencies()
        }
    }
}
