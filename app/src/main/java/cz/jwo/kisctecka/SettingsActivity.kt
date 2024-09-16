package cz.jwo.kisctecka

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
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

            flashBrightnessPref = findPreference("flash_brightness")!!
            flashEnabledPref = findPreference("flash_on_read")!!
            flashOnlyOnSuccess = findPreference("flash_only_on_success")!!

            flashBrightnessPref.isVisible = MainActivity.getTorchBrightnessRegulationAvailable(requireContext())

            refreshPreferenceDependencies()

            preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)

            findPreference<Preference>("version")!!.apply {
                setOnPreferenceClickListener {
                    startActivity(Intent(requireContext(), VersionActivity::class.java))
                    true
                }
                summary = getString(R.string.settings_app_version_summary, BuildConfig.VERSION_NAME)
            }
            findPreference<Preference>("github")!!
                .setOnPreferenceClickListener {
                    launchGitHub(requireContext())
                    true
                }
            findPreference<Preference>("discord")!!
                .setOnPreferenceClickListener {
                    launchDiscord(requireContext())
                    true
                }
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
