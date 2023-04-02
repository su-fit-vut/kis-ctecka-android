package cz.jwo.kisctecka

import android.app.WallpaperManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class VersionActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_version)

        window.apply {
            navigationBarColor = Color.BLACK
        }

        setSupportActionBar(findViewById(R.id.toolbar))

        // Get version information.
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val versionName = packageInfo.versionName
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            packageInfo.versionCode
        }
        val firstInstallDate = Date(packageInfo.firstInstallTime)
        val lastUpdateDate = Date(packageInfo.lastUpdateTime)

        val dateFormat = SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.LONG, SimpleDateFormat.LONG)

        // Populate text views with application information.
        listOf(
            Pair(R.id.version_name_in_title, "v$versionName"),
            Pair(R.id.version_name, versionName),
            Pair(R.id.version_code, versionCode.toString()),
            Pair(R.id.version_package_name, packageName),
            Pair(R.id.first_install_date, dateFormat.format(firstInstallDate)),
            Pair(R.id.last_install_date, dateFormat.format(lastUpdateDate)),
        ).forEach { (textViewId, value) ->
            findViewById<TextView>(textViewId)!!.text = value
        }

        findViewById<Button>(R.id.version_github_button).setOnClickListener {
            launchGitHub(this)
        }
        findViewById<Button>(R.id.version_discord_button).setOnClickListener {
            launchDiscord(this)
        }
    }

    override fun onNavigateUp(): Boolean {
        finish()
        return true
    }
}
