package hub.vpn

import android.app.Activity
import android.content.*
import android.net.VpnService
import android.os.*
import android.view.Gravity
import android.view.View
import android.widget.*
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {
    private lateinit var input: EditText
    private lateinit var spinner: Spinner
    private lateinit var status: TextView
    private lateinit var connect: Button
    private var servers: List<Server> = emptyList()
    private var connected = false
    private val ui = Handler(Looper.getMainLooper())

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            val m = i?.getStringExtra("msg") ?: return
            status.text = m
            connected = m == "Подключено"
            connect.text = if (connected) "Отключить" else "Подключить"
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }

        val size = (96 * resources.displayMetrics.density).toInt()
        root.addView(ImageView(this).apply { setImageResource(R.drawable.avatar); layoutParams = LinearLayout.LayoutParams(size, size) })
        root.addView(TextView(this).apply { text = "Паштет VPN"; textSize = 28f })
        root.addView(TextView(this).apply { text = "Вставь ссылку подписки (https://...) или сами ключи vless:// vmess:// trojan:// ss://" })
        input = EditText(this).apply { minLines = 3; gravity = Gravity.TOP
            setText(getSharedPreferences("hub", 0).getString("sub", "")) }
        root.addView(input)
        root.addView(Button(this).apply { text = "Загрузить серверы"; setOnClickListener { load() } })
        spinner = Spinner(this); root.addView(spinner)
        connect = Button(this).apply { text = "Подключить"; setOnClickListener { toggle() } }
        root.addView(connect)
        status = TextView(this).apply { text = "Готово к работе" }
        root.addView(status)
        setContentView(ScrollView(this).apply { addView(root) })
        registerReceiver(receiver, IntentFilter("hub.vpn.STATUS"))
    }

    override fun onDestroy() { unregisterReceiver(receiver); super.onDestroy() }

    private fun load() {
        val text = input.text.toString().trim()
        getSharedPreferences("hub", 0).edit().putString("sub", text).apply()
        status.text = "Загрузка..."
        Thread {
            val result = try {
                val raw = if (text.startsWith("http://") || text.startsWith("https://")) fetch(text) else text
                Parser.parseAll(raw) to null
            } catch (e: Exception) { emptyList<Server>() to (e.message ?: e.javaClass.simpleName) }
            ui.post {
                servers = result.first
                spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
                    servers.map { "${it.name} (${it.proto})" })
                status.text = when {
                    result.second != null -> "Ошибка загрузки: ${result.second}. Если пишет про SSL, открой ссылку на компьютере и вставь сюда текст вместо ссылки."
                    servers.isEmpty() -> "Серверы не найдены"
                    else -> "Найдено серверов: ${servers.size}"
                }
            }
        }.start()
    }

    private fun fetch(u: String): String {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 15000
        c.setRequestProperty("User-Agent", "HubVPN/0.1")
        return c.inputStream.bufferedReader().use { it.readText() }
    }

    private fun toggle() {
        if (connected) { startService(Intent(this, HubVpnService::class.java).setAction("stop")); return }
        if (servers.isEmpty()) { status.text = "Сначала загрузи серверы"; return }
        val i = VpnService.prepare(this)
        if (i != null) startActivityForResult(i, 1) else startVpn()
    }

    override fun onActivityResult(req: Int, res: Int, d: Intent?) {
        if (req == 1 && res == RESULT_OK) startVpn() else if (req == 1) status.text = "Разрешение на VPN не выдано"
    }

    private fun startVpn() {
        val s = servers[spinner.selectedItemPosition]
        startService(Intent(this, HubVpnService::class.java).putExtra("config", XrayConfig.build(s)))
    }
}
