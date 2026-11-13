package hub.vpn

import android.app.Activity
import android.content.*
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.VpnService
import android.os.*
import android.view.*
import android.widget.*

class PowerView(c: Context) : View(c) {
    var on = false
        set(v) { field = v; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val cx = w / 2
        val cy = height / 2f
        val r = w / 2 - 6f
        p.style = Paint.Style.FILL
        p.color = if (on) 0xFF3B3470.toInt() else 0xFF22242A.toInt()
        cv.drawCircle(cx, cy, r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 6f
        p.color = if (on) 0xFF7C6CF0.toInt() else 0xFF3A3D47.toInt()
        cv.drawCircle(cx, cy, r, p)
        p.strokeWidth = w * 0.045f
        p.strokeCap = Paint.Cap.ROUND
        p.color = if (on) 0xFFB8AEFF.toInt() else 0xFF8B8F9A.toInt()
        val ir = w * 0.2f
        cv.drawArc(RectF(cx - ir, cy - ir, cx + ir, cy + ir), -60f, 300f, false, p)
        cv.drawLine(cx, cy - ir * 1.15f, cx, cy, p)
    }
}

class MainActivity : Activity() {
    private val BG = 0xFF17181C.toInt()
    private val CARD = 0xFF22242A.toInt()
    private val SEL = 0xFF2C2A45.toInt()
    private val ACC = 0xFF7C6CF0.toInt()
    private val TXT = 0xFFE8E8EE.toInt()
    private val MUTED = 0xFF8B8F9A.toInt()

    private lateinit var input: EditText
    private lateinit var power: PowerView
    private lateinit var state: TextView
    private lateinit var timer: TextView
    private lateinit var msg: TextView
    private lateinit var listBox: LinearLayout
    private var servers: List<Server> = emptyList()
    private var selected = 0
    private var connected = false
    private var since = 0L
    private val ui = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            val s = (SystemClock.elapsedRealtime() - since) / 1000
            timer.text = String.format("%02d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
            ui.postDelayed(this, 1000)
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            val m = i?.getStringExtra("msg") ?: return
            if (m == "Подключено") setConnected(true)
            else { if (m == "Отключено") setConnected(false); msg.text = m }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, r: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(r).toFloat() }

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        actionBar?.hide()
        window.statusBarColor = BG
        window.navigationBarColor = BG
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(24)) }

        // шапка: аватарка и название
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val av = ImageView(this).apply {
            setImageResource(R.drawable.avatar)
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) { o.setOval(0, 0, v.width, v.height) }
            }
            clipToOutline = true
        }
        head.addView(av, LinearLayout.LayoutParams(dp(44), dp(44)))
        head.addView(label("Паштет VPN", 22f, TXT, true).apply { setPadding(dp(12), 0, 0, 0) })
        head.addView(label("v0.3", 12f, MUTED).apply { setPadding(dp(8), 0, 0, 0) })
        root.addView(head)

        // подписка
        val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = rounded(CARD, 14); setPadding(dp(14), dp(12), dp(14), dp(12)) }
        card.addView(label("Подписка", 13f, MUTED))
        input = EditText(this).apply {
            hint = "Ссылка подписки или ключи"; setHintTextColor(MUTED); setTextColor(TXT); textSize = 14f
            maxLines = 3; background = rounded(0xFF1B1C21.toInt(), 10); setPadding(dp(12), dp(10), dp(12), dp(10))
            setText(getSharedPreferences("hub", 0).getString("sub", ""))
        }
        card.addView(input, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(6), 0, dp(8)) })
        card.addView(Button(this).apply {
            text = "Обновить серверы"; setTextColor(Color.WHITE); isAllCaps = false
            background = rounded(ACC, 10); setOnClickListener { load() }
        }, LinearLayout.LayoutParams(-1, dp(44)))
        root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(16), 0, 0) })

        // кнопка питания
        power = PowerView(this).apply { setOnClickListener { toggle() } }
        root.addView(power, LinearLayout.LayoutParams(dp(150), dp(150)).apply { gravity = Gravity.CENTER_HORIZONTAL; setMargins(0, dp(24), 0, dp(8)) })
        state = label("ОТКЛЮЧЕНО", 14f, MUTED, true).apply { gravity = Gravity.CENTER }
        root.addView(state, LinearLayout.LayoutParams(-1, -2))
        timer = label("", 13f, MUTED).apply { gravity = Gravity.CENTER }
        root.addView(timer, LinearLayout.LayoutParams(-1, -2))
        msg = label("", 13f, MUTED).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(8)) }
        root.addView(msg, LinearLayout.LayoutParams(-1, -2))

        // серверы
        root.addView(label("Серверы", 16f, TXT, true).apply { setPadding(0, dp(8), 0, dp(8)) })
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listBox, LinearLayout.LayoutParams(-1, -2))

        setContentView(ScrollView(this).apply { setBackgroundColor(BG); addView(root) })
        registerReceiver(receiver, IntentFilter("hub.vpn.STATUS"))
        if (input.text.isNotBlank()) load()
    }

    override fun onDestroy() { ui.removeCallbacks(tick); unregisterReceiver(receiver); super.onDestroy() }

    private fun setConnected(v: Boolean) {
        connected = v
        power.on = v
        state.text = if (v) "ПОДКЛЮЧЕНО" else "ОТКЛЮЧЕНО"
        state.setTextColor(if (v) ACC else MUTED)
        ui.removeCallbacks(tick)
        if (v) { since = SystemClock.elapsedRealtime(); ui.post(tick) } else timer.text = ""
    }

    private fun renderServers() {
        listBox.removeAllViews()
        for (i in servers.indices) {
            val s = servers[i]
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = rounded(if (i == selected) SEL else CARD, 12)
                setPadding(dp(14), dp(12), dp(14), dp(12))
                setOnClickListener { selected = i; renderServers() }
            }
            row.addView(label(s.name, 16f, TXT, true))
            row.addView(label("${s.proto.uppercase()} / ${(s.params["type"] ?: "tcp").uppercase()} / ${(s.params["security"] ?: "").uppercase()}", 12f, MUTED))
            listBox.addView(row, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(8)) })
        }
    }

    private fun load() {
        val text = input.text.toString().trim()
        getSharedPreferences("hub", 0).edit().putString("sub", text).apply()
        msg.text = "Загрузка..."
        Thread {
            val result = try {
                val raw = if (text.startsWith("http://") || text.startsWith("https://")) Net.fetch(this, text) else text
                Parser.parseAll(raw) to null
            } catch (e: Exception) { emptyList<Server>() to (e.message ?: e.javaClass.simpleName) }
            ui.post {
                servers = result.first
                selected = 0
                renderServers()
                msg.text = when {
                    result.second != null -> "Ошибка загрузки: ${result.second}"
                    servers.isEmpty() -> "Серверы не найдены"
                    else -> "Найдено серверов: ${servers.size}"
                }
            }
        }.start()
    }

    private fun toggle() {
        if (connected) { startService(Intent(this, HubVpnService::class.java).setAction("stop")); return }
        if (servers.isEmpty()) { msg.text = "Сначала загрузи серверы"; return }
        val i = VpnService.prepare(this)
        if (i != null) startActivityForResult(i, 1) else startVpn()
    }

    override fun onActivityResult(req: Int, res: Int, d: Intent?) {
        if (req == 1 && res == RESULT_OK) startVpn() else if (req == 1) msg.text = "Разрешение на VPN не выдано"
    }

    private fun startVpn() {
        val s = servers[selected]
        startService(Intent(this, HubVpnService::class.java).putExtra("config", XrayConfig.build(s)))
    }
}
