package hub.vpn

import android.util.Base64
import org.json.JSONObject
import java.net.URLDecoder

data class Server(
    val name: String, val proto: String, val host: String, val port: Int,
    val secret: String, val params: Map<String, String>
)

object Parser {
    private fun b64(s: String): String {
        var t = s.trim().replace('-', '+').replace('_', '/')
        while (t.length % 4 != 0) t += "="
        return String(Base64.decode(t, Base64.DEFAULT), Charsets.UTF_8)
    }

    private fun dec(s: String) = try { URLDecoder.decode(s, "UTF-8") } catch (e: Exception) { s }

    /** Принимает ссылки vless:// vmess:// trojan:// ss:// построчно или всё это в base64 (как отдаёт подписка). */
    fun parseAll(raw: String): List<Server> {
        var text = raw.trim()
        if (!text.contains("://")) text = try { b64(text) } catch (e: Exception) { return emptyList() }
        return text.lines().mapNotNull { parseLine(it.trim()) }
    }

    private fun parseLine(line: String): Server? = try {
        when {
            line.startsWith("vless://") -> urlLike("vless", line)
            line.startsWith("trojan://") -> urlLike("trojan", line)
            line.startsWith("vmess://") -> vmess(line)
            line.startsWith("ss://") -> ss(line)
            else -> null
        }
    } catch (e: Exception) { null }

    private fun urlLike(proto: String, line: String): Server {
        val rest = line.substring(proto.length + 3)
        val name = dec(rest.substringAfter('#', ""))
        val noFrag = rest.substringBefore('#')
        val query = noFrag.substringAfter('?', "")
        val main = noFrag.substringBefore('?')
        val hostPort = main.substringAfter('@')
        val host = hostPort.substringBeforeLast(':').trim('[', ']')
        val port = hostPort.substringAfterLast(':').toInt()
        val params = query.split('&').filter { it.contains('=') }
            .associate { it.substringBefore('=') to dec(it.substringAfter('=')) }
        return Server(name.ifEmpty { host }, proto, host, port, dec(main.substringBefore('@')), params)
    }

    private fun vmess(line: String): Server {
        val j = JSONObject(b64(line.substring(8)))
        val p = mutableMapOf(
            "type" to j.optString("net", "tcp"), "security" to j.optString("tls", ""),
            "sni" to j.optString("sni", ""), "host" to j.optString("host", ""),
            "path" to j.optString("path", ""), "aid" to j.optString("aid", "0"),
            "scy" to j.optString("scy", "auto")
        )
        val host = j.getString("add")
        return Server(j.optString("ps", host).ifEmpty { host }, "vmess", host, j.get("port").toString().toInt(), j.getString("id"), p)
    }

    private fun ss(line: String): Server {
        val rest = line.substring(5)
        val name = dec(rest.substringAfter('#', ""))
        val main = rest.substringBefore('#').substringBefore('?')
        val userinfo: String
        val hp: String
        if (main.contains('@')) { userinfo = main.substringBeforeLast('@'); hp = main.substringAfterLast('@') }
        else { val d = b64(main); userinfo = d.substringBeforeLast('@'); hp = d.substringAfterLast('@') }
        val ui = if (userinfo.contains(':')) userinfo else b64(userinfo)
        val host = hp.substringBeforeLast(':')
        return Server(name.ifEmpty { host }, "ss", host, hp.substringAfterLast(':').toInt(),
            ui.substringAfter(':'), mapOf("method" to ui.substringBefore(':')))
    }
}
