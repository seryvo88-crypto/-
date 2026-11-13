package hub.vpn

import android.os.ParcelFileDescriptor

/**
 * Ядро VPN. На этапе 1 его нет: приложение умеет загрузить подписку, разобрать серверы,
 * собрать конфиг Xray и поднять системный VPN-интерфейс. На этапе 2 здесь подключается
 * Xray (libv2ray.aar) и tun2socks, которые гоняют трафик из tun-интерфейса через SOCKS 127.0.0.1:10808.
 */
object Core {
    const val AVAILABLE = false

    fun start(configJson: String, tun: ParcelFileDescriptor, protect: (Int) -> Boolean): Boolean {
        if (!AVAILABLE) return false
        // TODO этап 2
        return true
    }

    fun stop() {
        // TODO этап 2
    }
}
