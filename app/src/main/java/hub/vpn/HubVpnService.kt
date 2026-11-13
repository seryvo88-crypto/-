package hub.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor

class HubVpnService : VpnService() {
    private var tun: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") { shutdown(); return START_NOT_STICKY }
        val cfg = intent?.getStringExtra("config")
        if (cfg == null) { stopSelf(); return START_NOT_STICKY }

        // Без ядра VPN-интерфейс не поднимаем: иначе весь трафик телефона пропадёт.
        if (!Core.AVAILABLE) {
            report("Ядро не подключено (этап 2). Интерфейс VPN не запущен.")
            stopSelf(); return START_NOT_STICKY
        }
        val b = Builder().setSession("Паштет VPN").setMtu(1500)
            .addAddress("10.10.14.1", 24).addRoute("0.0.0.0", 0).addDnsServer("1.1.1.1")
        try { b.addDisallowedApplication(packageName) } catch (e: Exception) { }
        tun = b.establish()
        val t = tun
        if (t == null || !Core.start(cfg, t) { protect(it) }) { report("Не удалось запустить ядро"); shutdown(); return START_NOT_STICKY }
        report("Подключено")
        return START_STICKY
    }

    private fun report(msg: String) {
        sendBroadcast(Intent("hub.vpn.STATUS").setPackage(packageName).putExtra("msg", msg))
    }

    private fun shutdown() {
        Core.stop()
        try { tun?.close() } catch (e: Exception) { }
        tun = null
        report("Отключено")
        stopSelf()
    }

    override fun onRevoke() { shutdown() }
    override fun onDestroy() { Core.stop(); try { tun?.close() } catch (e: Exception) { }; super.onDestroy() }
}
