package com.rocktek.customlauncher

import android.app.Activity
import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var root: FrameLayout
    private lateinit var content: LinearLayout
    private lateinit var wallpaper: ImageView
    private lateinit var status: TextView
    private var selectedCategory = "Избранное"
    private var city = "Stockholm"
    private val executor = Executors.newSingleThreadExecutor()
    private val apps = mutableListOf<AppEntry>()
    private val favorites = linkedSetOf<String>()

    data class AppEntry(val label: String, val packageName: String, val icon: android.graphics.drawable.Drawable)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("launcher_settings", MODE_PRIVATE)
        favorites.addAll(prefs.getStringSet("favorites", emptySet()) ?: emptySet())
        city = prefs.getString("city", "Stockholm") ?: "Stockholm"
        buildInterface()
        loadApps()
        restoreWallpaper()
        refreshClock()
        loadWeather()
    }

    private fun buildInterface() {
        root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(12, 18, 31)) }
        wallpaper = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0.24f
        }
        root.addView(wallpaper, FrameLayout.LayoutParams(-1, -1))
        val shade = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.argb(190, 7, 13, 25), Color.argb(220, 10, 18, 34))
            )
        }
        root.addView(shade, FrameLayout.LayoutParams(-1, -1))

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(42), dp(24), dp(42), dp(22))
        }
        root.addView(content, FrameLayout.LayoutParams(-1, -1))

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brand = TextView(this).apply {
            text = "ROCKTEK"
            textSize = 24f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        top.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        status = TextView(this).apply {
            text = "--:--   •   Погода загружается"
            textSize = 16f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
        }
        top.addView(status)
        content.addView(top)

        val subtitle = TextView(this).apply {
            text = "Ваш домашний экран"
            textSize = 14f
            setTextColor(Color.rgb(160, 177, 205))
            setPadding(0, dp(4), 0, dp(18))
        }
        content.addView(subtitle)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        actions.addView(actionButton("Обои") { chooseWallpaper() })
        actions.addView(actionButton("Город") { editCity() })
        actions.addView(actionButton("Настройки") { showHelp() })
        content.addView(actions)

        val heading = TextView(this).apply {
            text = "ПРИЛОЖЕНИЯ"
            textSize = 13f
            letterSpacing = 0.12f
            setTextColor(Color.rgb(145, 166, 199))
            setPadding(0, dp(22), 0, dp(10))
        }
        content.addView(heading)

        val categoryScroll = horizontalScroll()
        val categories = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Избранное", "Все", "ТВ и видео", "Музыка", "Система").forEach { category ->
            categories.addView(actionButton(category) {
                selectedCategory = category
                renderApps()
            })
        }
        categoryScroll.addView(categories)
        content.addView(categoryScroll)

        val scroll = ScrollView(this)
        val grid = android.widget.GridLayout(this).apply {
            columnCount = 5
            useDefaultMargins = true
            alignmentMode = android.widget.GridLayout.ALIGN_BOUNDS
        }
        appGrid = grid
        scroll.addView(grid)
        content.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val footer = TextView(this).apply {
            text = "OK — открыть   •   удерживайте OK — избранное   •   Projectivy сохранён как резервный лаунчер"
            textSize = 12f
            setTextColor(Color.rgb(130, 148, 177))
            setPadding(0, dp(10), 0, 0)
        }
        content.addView(footer)
        setContentView(root)
    }

    private lateinit var appGrid: android.widget.GridLayout

    private fun loadApps() {
        apps.clear()
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        resolved.forEach { info ->
            val pkg = info.activityInfo.packageName
            if (pkg != packageName) {
                apps.add(AppEntry(
                    info.loadLabel(packageManager)?.toString() ?: pkg,
                    pkg,
                    info.loadIcon(packageManager)
                ))
            }
        }
        apps.sortBy { it.label.lowercase(Locale.getDefault()) }
        renderApps()
    }

    private fun renderApps() {
        appGrid.removeAllViews()
        val visible = apps.filter { app ->
            when (selectedCategory) {
                "Избранное" -> favorites.contains(app.packageName)
                "ТВ и видео" -> matches(app, listOf("tv", "video", "iptv", "youtube", "netflix", "plex", "ivi", "wink", "tvirl", "smarttube"))
                "Музыка" -> matches(app, listOf("music", "spotify", "wiim", "tidal", "radio", "audio"))
                "Система" -> matches(app, listOf("setting", "file", "manager", "update", "system"))
                else -> true
            }
        }
        if (visible.isEmpty()) {
            val empty = TextView(this).apply {
                text = if (selectedCategory == "Избранное") "Здесь пока пусто. Удерживайте OK на приложении, чтобы добавить его." else "Приложения этой категории не найдены."
                textSize = 16f
                setTextColor(Color.LTGRAY)
                setPadding(dp(12), dp(24), dp(12), dp(24))
            }
            appGrid.addView(empty)
            return
        }
        visible.forEach { app ->
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isFocusable = true
                isFocusableInTouchMode = true
                background = rounded(Color.argb(95, 255, 255, 255), dp(14))
                setPadding(dp(8), dp(12), dp(8), dp(12))
                setOnFocusChangeListener { v, hasFocus ->
                    v.scaleX = if (hasFocus) 1.06f else 1f
                    v.scaleY = if (hasFocus) 1.06f else 1f
                    v.background = rounded(if (hasFocus) Color.rgb(37, 99, 235) else Color.argb(95, 255, 255, 255), dp(14))
                }
                setOnClickListener { launchApp(app.packageName) }
                setOnLongClickListener {
                    if (favorites.contains(app.packageName)) favorites.remove(app.packageName) else favorites.add(app.packageName)
                    prefs.edit().putStringSet("favorites", favorites.toSet()).apply()
                    renderApps()
                    true
                }
            }
            val icon = ImageView(this).apply {
                setImageDrawable(app.icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            tile.addView(icon, LinearLayout.LayoutParams(dp(54), dp(54)))
            val label = TextView(this).apply {
                text = app.label
                textSize = 12f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                maxLines = 2
                setPadding(0, dp(8), 0, 0)
            }
            tile.addView(label, LinearLayout.LayoutParams(-1, dp(38)))
            val lp = android.widget.GridLayout.LayoutParams().apply {
                width = 0
                height = dp(112)
                columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
                setMargins(dp(5), dp(5), dp(5), dp(5))
            }
            appGrid.addView(tile, lp)
        }
        if (appGrid.childCount > 0) appGrid.getChildAt(0).requestFocus()
    }

    private fun matches(app: AppEntry, words: List<String>): Boolean {
        val value = (app.label + " " + app.packageName).lowercase(Locale.ROOT)
        return words.any { value.contains(it) }
    }

    private fun launchApp(pkg: String) {
        try {
            val launch = packageManager.getLaunchIntentForPackage(pkg)
            if (launch != null) startActivity(launch)
        } catch (_: Exception) { }
    }

    private fun actionButton(text: String, action: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            textSize = 13f
            isFocusable = true
            setTextColor(Color.WHITE)
            background = rounded(Color.argb(100, 68, 91, 130), dp(12))
            setPadding(dp(10), dp(2), dp(10), dp(2))
            setOnClickListener { action() }
            val lp = LinearLayout.LayoutParams(-2, dp(44))
            lp.setMargins(0, 0, dp(8), 0)
            layoutParams = lp
        }
    }

    private fun horizontalScroll() = android.widget.HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
    }

    private fun chooseWallpaper() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, 101)
    }

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 101 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                prefs.edit().putString("wallpaper_uri", uri.toString()).apply()
                wallpaper.setImageURI(uri)
            } catch (_: Exception) {
                wallpaper.setImageURI(uri)
            }
        }
    }

    private fun restoreWallpaper() {
        val saved = prefs.getString("wallpaper_uri", null) ?: return
        try { wallpaper.setImageURI(Uri.parse(saved)) } catch (_: Exception) { }
    }

    private fun editCity() {
        val input = EditText(this).apply {
            setText(city)
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("Город для погоды")
            .setView(input)
            .setPositiveButton("Сохранить") { _, _ ->
                city = input.text.toString().trim().ifBlank { "Stockholm" }
                prefs.edit().putString("city", city).apply()
                loadWeather()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun loadWeather() {
        val requestedCity = city
        status.text = "Погода: $requestedCity…"
        executor.execute {
            try {
                val geoUrl = URL("https://geocoding-api.open-meteo.com/v1/search?name=${java.net.URLEncoder.encode(requestedCity, "UTF-8")}&count=1&language=ru&format=json")
                val geo = JSONObject(readUrl(geoUrl))
                val result = geo.optJSONArray("results")?.optJSONObject(0)
                if (result == null) {
                    runOnUiThread { status.text = "Город не найден • ${java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date())}" }
                    return@execute
                }
                val lat = result.getDouble("latitude")
                val lon = result.getDouble("longitude")
                val weather = JSONObject(readUrl(URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code&timezone=auto")))
                val current = weather.optJSONObject("current")
                val temp = current?.optDouble("temperature_2m")
                val label = if (temp != null && !temp.isNaN()) "${temp.toInt()}°C" else "—"
                runOnUiThread {
                    val time = java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date())
                    status.text = "$time   •   $requestedCity   •   $label"
                }
            } catch (_: Exception) {
                runOnUiThread {
                    val time = java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date())
                    status.text = "$time   •   $requestedCity   •   нет данных о погоде"
                }
            }
        }
    }

    private fun readUrl(url: URL): String {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 7000
            readTimeout = 7000
            requestMethod = "GET"
        }
        return try { connection.inputStream.bufferedReader().use { it.readText() } }
        finally { connection.disconnect() }
    }

    private fun refreshClock() {
        val time = java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date())
        if (status.text.toString().startsWith("--:--")) status.text = time
    }

    private fun showHelp() {
        AlertDialog.Builder(this)
            .setTitle("RockTek Custom Launcher")
            .setMessage("• Обои сохраняются после перезапуска.\n• Удерживайте OK на приложении, чтобы добавить или убрать его из избранного.\n• Город можно изменить кнопкой «Город».\n• Чтобы вернуться к Projectivy, откройте настройки Android → Приложения → Приложения по умолчанию → Главный экран.")
            .setPositiveButton("Понятно", null)
            .show()
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdownNow()
    }
}
