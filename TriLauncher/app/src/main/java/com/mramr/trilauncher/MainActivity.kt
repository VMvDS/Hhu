package com.mramr.trilauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import java.io.File

class MainActivity : Activity() {

    class App(val label: String, val comp: ComponentName, val info: ResolveInfo) {
        val key: String get() = comp.flattenToString()
    }

    private var allApps = listOf<App>()
    private var visible = listOf<App>()
    private var shown = listOf<App>()
    private val prefs by lazy { getSharedPreferences("tri", MODE_PRIVATE) }

    private lateinit var bg: ImageView
    private lateinit var scrim: View
    private lateinit var homeRoot: FrameLayout
    private lateinit var drawer: LinearLayout
    private lateinit var grid: GridView
    private lateinit var search: EditText
    private lateinit var gd: GestureDetector
    private var drawerOpen = false

    // OLED palette
    private val white = 0xFFF4F4F2.toInt()
    private val grey = 0xFF777777.toInt()
    private val softGrey = 0xFFB5B5B2.toInt()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun scrW() = resources.displayMetrics.widthPixels
    private fun scrH() = resources.displayMetrics.heightPixels

    // ---------- small view helpers ----------

    private fun txt(s: String, sp: Float, color: Int, tf: Typeface, spacing: Float = 0f): TextView =
        TextView(this).apply {
            text = s
            textSize = sp
            setTextColor(color)
            typeface = tf
            letterSpacing = spacing
            includeFontPadding = false
        }

    private fun clockView(fmt12: String, fmt24: String, sp: Float, color: Int, tf: Typeface, spacing: Float = 0f): TextClock =
        TextClock(this).apply {
            format12Hour = fmt12
            format24Hour = fmt24
            textSize = sp
            setTextColor(color)
            typeface = tf
            letterSpacing = spacing
            includeFontPadding = false
        }

    private fun rule(wPx: Int, alpha: Int = 200): View = View(this).apply {
        setBackgroundColor(Color.argb(alpha, 255, 255, 255))
        layoutParams = LinearLayout.LayoutParams(wPx, dp(1))
    }

    private fun battery(): String {
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        return "BATTERY " + bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) + "%"
    }

    private fun soft(v: View) {
        v.setOnTouchListener { x, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> x.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    x.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
            }
            false
        }
    }

    /** Line-art icon; tile style 0 = soft white tile (reference), 1 = bare white line icon. */
    private fun iconView(a: App, sizePx: Int): ImageView {
        val iv = ImageView(this)
        val kind = LineIcon.kindOf(a.comp.packageName, a.label)
        val letter = a.label.take(1).uppercase()
        if (prefs.getInt("tile", 0) == 0) {
            iv.setImageDrawable(LineIcon(kind, letter, 0xFF2A2A2A.toInt()))
            val pad = (sizePx * 0.2f).toInt()
            iv.setPadding(pad, pad, pad, pad)
            iv.background = GradientDrawable().apply {
                setColor(0xF2F4F4F2.toInt())
                cornerRadius = sizePx * 0.28f
            }
        } else {
            iv.setImageDrawable(LineIcon(kind, letter, white))
            val pad = (sizePx * 0.1f).toInt()
            iv.setPadding(pad, pad, pad, pad)
        }
        return iv
    }

    // ---------- lifecycle ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        bg = ImageView(this)
        bg.scaleType = ImageView.ScaleType.CENTER_CROP
        root.addView(bg, FrameLayout.LayoutParams(-1, -1))
        scrim = View(this)
        root.addView(scrim, FrameLayout.LayoutParams(-1, -1))

        homeRoot = FrameLayout(this)
        homeRoot.setOnLongClickListener { showMenu(); true }
        root.addView(homeRoot, FrameLayout.LayoutParams(-1, -1))

        drawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(if (Build.VERSION.SDK_INT >= 31) Color.argb(185, 0, 0, 0) else Color.argb(244, 0, 0, 0))
            setPadding(dp(22), dp(56), dp(22), dp(12))
            visibility = View.GONE
        }
        drawer.addView(txt("ALL APPS", 11f, grey, Typeface.create("sans-serif-medium", Typeface.NORMAL), 0.3f))
        search = EditText(this).apply {
            hint = "Search"
            setHintTextColor(grey)
            setTextColor(white)
            textSize = 17f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            isSingleLine = true
            background = null
            setPadding(0, dp(10), 0, dp(10))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val q = s?.toString()?.trim().orEmpty()
                    shown = if (q.isEmpty()) visible else visible.filter { it.label.contains(q, true) }
                    (grid.adapter as BaseAdapter).notifyDataSetChanged()
                }
            })
        }
        drawer.addView(search, LinearLayout.LayoutParams(-1, -2))
        drawer.addView(rule(1, 60).apply {
            layoutParams = LinearLayout.LayoutParams(-1, dp(1))
        })
        grid = GridView(this).apply {
            numColumns = 4
            verticalSpacing = dp(20)
            setSelector(android.R.color.transparent)
            adapter = Ad()
            setOnItemClickListener { _, _, p, _ -> launch(shown[p]) }
            setOnItemLongClickListener { _, _, p, _ -> appMenu(shown[p]); true }
        }
        drawer.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(18) })
        root.addView(drawer, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (Math.abs(vy) < Math.abs(vx)) return false
                if (!drawerOpen && vy < -1200) openDrawer()
                else if (!drawerOpen && vy > 1200 && prefs.getInt("swipeDown", 0) == 0) pullShade()
                else if (drawerOpen && vy > 1500 && !grid.canScrollVertically(-1)) closeDrawer()
                return false
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (drawerOpen) return false
                when (prefs.getInt("dtap", 0)) {
                    0 -> showMenu()
                    1 -> switchLayout()
                }
                return true
            }
        })
        applyLook()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gd.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (drawerOpen) closeDrawer()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (drawerOpen) closeDrawer()
    }

    // ---------- look and settings ----------

    private fun applyLook() {
        val k = prefs.getString("wp", "hole") ?: "hole"
        val f = File(filesDir, "custom.jpg")
        when {
            k == "custom" && f.exists() -> bg.setImageBitmap(BitmapFactory.decodeFile(f.path))
            k == "coast" -> bg.setImageResource(R.drawable.wp_coast)
            k == "black" -> bg.setImageDrawable(ColorDrawable(Color.BLACK))
            else -> bg.setImageResource(R.drawable.wp_blackhole)
        }
        val a = intArrayOf(0, 70, 140)[prefs.getInt("dim", 1).coerceIn(0, 2)]
        scrim.setBackgroundColor(Color.argb(a, 0, 0, 0))
        buildHome()
    }

    private fun dlg() = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)

    private fun showMenu() {
        dlg().setTitle("TriLauncher")
            .setItems(arrayOf(
                "Layout", "Wallpaper", "Home shortcuts", "Hidden apps",
                "Icon style", "Photo dimming", "Gestures"
            )) { _, w ->
                when (w) {
                    0 -> pickLayout()
                    1 -> pickWallpaper()
                    2 -> chooseFavs()
                    3 -> chooseHidden()
                    4 -> pickTile()
                    5 -> pickDim()
                    6 -> pickGestures()
                }
            }.show()
    }

    private fun setLayout(i: Int) {
        prefs.edit().putInt("layout", i)
            .putString("wp", if (i == 0) "coast" else "hole").apply()
        applyLook()
    }

    private fun switchLayout() = setLayout(if (prefs.getInt("layout", 0) == 0) 1 else 0)

    private fun pickLayout() {
        dlg().setTitle("Layout").setItems(arrayOf("Dark Coast (icon column)", "Black Hole (editorial)")) { _, i ->
            setLayout(i)
        }.show()
    }

    private fun pickWallpaper() {
        dlg().setTitle("Wallpaper").setItems(
            arrayOf("Dark Coast", "Black Hole (pure OLED)", "Pure black", "My photo from gallery")
        ) { _, i ->
            when (i) {
                0 -> { prefs.edit().putString("wp", "coast").apply(); applyLook() }
                1 -> { prefs.edit().putString("wp", "hole").apply(); applyLook() }
                2 -> { prefs.edit().putString("wp", "black").apply(); applyLook() }
                else -> startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), 11)
            }
        }.show()
    }

    private fun pickTile() {
        dlg().setTitle("Icon style").setItems(arrayOf("Soft white tiles (reference)", "Bare white line icons")) { _, i ->
            prefs.edit().putInt("tile", i).apply()
            applyLook()
            (grid.adapter as BaseAdapter).notifyDataSetChanged()
        }.show()
    }

    private fun pickDim() {
        dlg().setTitle("Photo dimming").setItems(arrayOf("None", "Medium", "Strong")) { _, i ->
            prefs.edit().putInt("dim", i).apply()
            applyLook()
        }.show()
    }

    private fun pickGestures() {
        dlg().setTitle("Gestures").setItems(arrayOf("Swipe down", "Double tap")) { _, w ->
            if (w == 0) {
                dlg().setTitle("Swipe down").setItems(arrayOf("Open notifications", "Nothing")) { _, i ->
                    prefs.edit().putInt("swipeDown", i).apply()
                }.show()
            } else {
                dlg().setTitle("Double tap").setItems(arrayOf("Open this menu", "Switch layout", "Nothing")) { _, i ->
                    prefs.edit().putInt("dtap", i).apply()
                }.show()
            }
        }.show()
    }

    private fun chooseFavs() {
        val apps = visible
        val cur = favApps().map { it.key }.toMutableList()
        val checked = BooleanArray(apps.size) { apps[it].key in cur }
        dlg().setTitle("Home shortcuts (first 6 are shown)")
            .setMultiChoiceItems(apps.map { it.label }.toTypedArray(), checked) { _, i, on ->
                val k = apps[i].key
                if (on) {
                    if (k !in cur) cur.add(k)
                } else {
                    cur.remove(k)
                }
                Unit
            }
            .setPositiveButton("Done") { _, _ ->
                prefs.edit().putString("favs", cur.joinToString("|")).apply()
                buildHome()
            }.show()
    }

    private fun chooseHidden() {
        val apps = allApps
        val hidden = HashSet(prefs.getStringSet("hidden", emptySet()) ?: emptySet())
        val checked = BooleanArray(apps.size) { apps[it].key in hidden }
        dlg().setTitle("Hide apps from the drawer")
            .setMultiChoiceItems(apps.map { it.label }.toTypedArray(), checked) { _, i, on ->
                if (on) hidden.add(apps[i].key) else hidden.remove(apps[i].key)
            }
            .setPositiveButton("Done") { _, _ ->
                prefs.edit().putStringSet("hidden", HashSet(hidden)).apply()
                load()
            }.show()
    }

    private fun appMenu(a: App) {
        dlg().setTitle(a.label).setItems(arrayOf("Pin to home", "Hide", "App info", "Uninstall")) { _, i ->
            when (i) {
                0 -> {
                    val cur = favApps().map { it.key }.toMutableList()
                    if (a.key !in cur) cur.add(0, a.key)
                    prefs.edit().putString("favs", cur.joinToString("|")).apply()
                    buildHome()
                    closeDrawer()
                }
                1 -> {
                    val hidden = HashSet(prefs.getStringSet("hidden", emptySet()) ?: emptySet())
                    hidden.add(a.key)
                    prefs.edit().putStringSet("hidden", HashSet(hidden)).apply()
                    load()
                }
                2 -> startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + a.comp.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                3 -> startActivity(
                    Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.comp.packageName))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }.show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode == 11 && resultCode == RESULT_OK && uri != null) savePhoto(uri)
    }

    private fun savePhoto(uri: Uri) {
        try {
            val o = BitmapFactory.Options()
            o.inJustDecodeBounds = true
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            var s = 1
            while (o.outWidth / s > 1600 || o.outHeight / s > 2400) s *= 2
            val o2 = BitmapFactory.Options()
            o2.inSampleSize = s
            val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) }
            if (bmp == null) {
                Toast.makeText(this, "Could not open that photo", Toast.LENGTH_SHORT).show()
                return
            }
            File(filesDir, "custom.jpg").outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            prefs.edit().putString("wp", "custom").apply()
            applyLook()
        } catch (e: Exception) {
            Toast.makeText(this, "Could not use that photo", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- drawer, launching ----------

    private fun openDrawer() {
        drawerOpen = true
        if (Build.VERSION.SDK_INT >= 31) {
            bg.setRenderEffect(RenderEffect.createBlurEffect(30f, 30f, Shader.TileMode.CLAMP))
        }
        bg.animate().scaleX(1.06f).scaleY(1.06f).setDuration(320).start()
        homeRoot.animate().alpha(0f).setDuration(200).start()
        drawer.visibility = View.VISIBLE
        drawer.alpha = 0f
        drawer.translationY = dp(50).toFloat()
        drawer.animate().alpha(1f).translationY(0f).setDuration(240).start()
    }

    private fun closeDrawer() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(search.windowToken, 0)
        drawerOpen = false
        search.setText("")
        if (Build.VERSION.SDK_INT >= 31) bg.setRenderEffect(null)
        bg.animate().scaleX(1f).scaleY(1f).setDuration(280).start()
        homeRoot.animate().alpha(1f).setDuration(240).start()
        drawer.animate().alpha(0f).translationY(dp(50).toFloat()).setDuration(200)
            .withEndAction { drawer.visibility = View.GONE }.start()
    }

    private fun pullShade() {
        try {
            val sb = getSystemService("statusbar")
            if (sb != null) sb.javaClass.getMethod("expandNotificationsPanel").invoke(sb)
        } catch (e: Exception) {
        }
    }

    private fun launch(a: App) {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(a.comp)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try { startActivity(i) } catch (e: Exception) { }
        if (drawerOpen) closeDrawer()
    }

    private fun load() {
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        allApps = packageManager.queryIntentActivities(q, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map {
                App(it.loadLabel(packageManager).toString(),
                    ComponentName(it.activityInfo.packageName, it.activityInfo.name), it)
            }
            .sortedBy { it.label.lowercase() }
        val hidden = prefs.getStringSet("hidden", emptySet()) ?: emptySet()
        visible = allApps.filter { it.key !in hidden }
        shown = visible
        (grid.adapter as BaseAdapter).notifyDataSetChanged()
        buildHome()
    }

    // ---------- shortcuts ----------

    private fun favApps(): List<App> {
        val saved = prefs.getString("favs", null)
        if (saved != null) {
            val m = allApps.associateBy { it.key }
            return saved.split("|").mapNotNull { m[it] }
        }
        val groups = listOf(
            listOf("instagram"), listOf("facebook"), listOf("settings"),
            listOf("messag", "mms"), listOf("youtube"), listOf("camera"),
            listOf("whatsapp"), listOf("chrome")
        )
        val used = HashSet<String>()
        val out = ArrayList<App>()
        for (g in groups) {
            val m = allApps.firstOrNull { a ->
                a.key !in used && g.any { t ->
                    a.comp.packageName.contains(t, true) || a.label.contains(t, true)
                }
            }
            if (m != null) { out.add(m); used.add(m.key) }
        }
        return out
    }

    // ---------- home layouts ----------

    private fun buildHome() {
        homeRoot.removeAllViews()
        if (allApps.isEmpty()) return
        if (prefs.getInt("layout", 0) == 0) buildCoast() else buildHole()
    }

    private fun place(v: View, left: Int, top: Int) {
        homeRoot.addView(v, FrameLayout.LayoutParams(-2, -2).apply {
            leftMargin = left
            topMargin = top
        })
    }

    private fun shortcut(a: App, sizePx: Int): ImageView {
        val iv = iconView(a, sizePx)
        iv.setOnClickListener { launch(a) }
        iv.setOnLongClickListener { appMenu(a); true }
        soft(iv)
        return iv
    }

    /** Layout 1: Dark Coast. Script clock top-left, mono date, left icon column. */
    private fun buildCoast() {
        val w = scrW()
        val h = scrH()
        val script = Typeface.create("cursive", Typeface.NORMAL)
        val mono = Typeface.create("monospace", Typeface.NORMAL)
        val light = Typeface.create("sans-serif-light", Typeface.NORMAL)

        // top rule + battery line
        val top = LinearLayout(this)
        top.orientation = LinearLayout.VERTICAL
        top.addView(rule((w * 0.11f).toInt()))
        top.addView(txt(battery(), 11f, softGrey, mono, 0.12f),
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8) })
        place(top, (w * 0.05f).toInt(), (h * 0.065f).toInt())

        place(clockView("hh:mm a", "HH:mm", 40f, white, script), (w * 0.2f).toInt(), (h * 0.095f).toInt())
        place(clockView("EEEE dd", "EEEE dd", 13f, softGrey, mono, 0.1f), (w * 0.29f).toInt(), (h * 0.142f).toInt())

        // maker label with underline
        val who = LinearLayout(this)
        who.orientation = LinearLayout.VERTICAL
        who.addView(txt("By Mr Amr", 14f, white, light, 0.04f))
        who.addView(rule((w * 0.2f).toInt(), 160),
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8) })
        place(who, (w * 0.53f).toInt(), (h * 0.185f).toInt())

        // vertical editorial label on the right edge
        place(VText(this, "DARK COAST", 10f, grey, Typeface.create("sans-serif-medium", Typeface.NORMAL), 0.4f),
            w - dp(30), (h * 0.40f).toInt())

        // left icon column
        val size = (w * 0.094f).toInt().coerceAtLeast(dp(36))
        val gap = ((h * 0.0865f) - size).toInt().coerceAtLeast(dp(8))
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        for (a in favApps().take(6)) {
            col.addView(shortcut(a, size), LinearLayout.LayoutParams(size, size).apply { bottomMargin = gap })
        }
        place(col, (w * 0.024f).toInt(), (h * 0.308f - size / 2f).toInt())
    }

    /** Layout 2: Black Hole editorial. Title, large thin clock, hairline, greeting, icon row. */
    private fun buildHole() {
        val w = scrW()
        val h = scrH()
        val med = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val mono = Typeface.create("monospace", Typeface.NORMAL)
        val thin = Typeface.create("sans-serif-thin", Typeface.NORMAL)

        place(clockView("h:mm", "HH:mm", 12f, white, med, 0.15f), dp(26), dp(54))
        val bat = txt(battery(), 10f, softGrey, mono, 0.14f)
        homeRoot.addView(bat, FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.TOP).apply {
            topMargin = dp(54)
            marginEnd = dp(26)
        })

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(28), 0, dp(28), 0)
        col.addView(txt("Black Hole", 24f, white, Typeface.create("sans-serif", Typeface.BOLD), 0.01f))
        col.addView(clockView("EEE, d MMM", "EEE, d MMM", 11f, softGrey, mono, 0.08f),
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(6) })
        col.addView(clockView("hh:mm", "HH:mm", 92f, white, thin, -0.02f),
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(6) })

        val line = LinearLayout(this)
        line.orientation = LinearLayout.HORIZONTAL
        line.gravity = Gravity.CENTER_VERTICAL
        line.addView(View(this).apply { setBackgroundColor(0x99FFFFFF.toInt()) }, LinearLayout.LayoutParams(0, dp(1), 1f))
        line.addView(View(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(white) }
        }, LinearLayout.LayoutParams(dp(7), dp(7)))
        col.addView(line, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val greet = when {
            hour < 12 -> "HAVE A GOOD MORNING"
            hour < 18 -> "HAVE A GOOD AFTERNOON"
            else -> "HAVE A GOOD EVENING"
        }
        val g = txt(greet, 9f, softGrey, med, 0.22f)
        g.gravity = Gravity.END
        col.addView(g, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        col.addView(txt("By Mr Amr", 9f, grey, mono, 0.1f).apply { gravity = Gravity.END },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })

        homeRoot.addView(col, FrameLayout.LayoutParams(-1, -2).apply { topMargin = (h * 0.49f).toInt() })

        place(VText(this, "BLACK HOLE", 10f, grey, med, 0.4f), w - dp(30), (h * 0.14f).toInt())

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER
        val sz = dp(40)
        for (a in favApps().take(5)) {
            val cell = LinearLayout(this)
            cell.gravity = Gravity.CENTER
            cell.addView(shortcut(a, sz), LinearLayout.LayoutParams(sz, sz))
            row.addView(cell, LinearLayout.LayoutParams(0, -2, 1f))
        }
        homeRoot.addView(row, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            bottomMargin = dp(70)
            leftMargin = dp(18)
            rightMargin = dp(18)
        })
    }

    // ---------- drawer adapter ----------

    inner class Ad : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(position: Int) = shown[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val a = shown[position]
            val l = LinearLayout(this@MainActivity)
            l.orientation = LinearLayout.VERTICAL
            l.gravity = Gravity.CENTER_HORIZONTAL
            l.addView(iconView(a, dp(52)), LinearLayout.LayoutParams(dp(52), dp(52)))
            l.addView(TextView(this@MainActivity).apply {
                text = a.label
                textSize = 10.5f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
                setTextColor(softGrey)
                typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
            return l
        }
    }
}
