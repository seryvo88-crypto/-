package hub.vpn

import org.json.JSONArray
import org.json.JSONObject

object XrayConfig {
    const val SOCKS_PORT = 10808

    fun build(s: Server): String {
        val p = s.params
        val out = JSONObject().put("tag", "proxy")
        when (s.proto) {
            "vless" -> out.put("protocol", "vless").put("settings", JSONObject().put("vnext", JSONArray().put(
                JSONObject().put("address", s.host).put("port", s.port).put("users", JSONArray().put(
                    JSONObject().put("id", s.secret).put("encryption", p["encryption"] ?: "none")
                        .apply { if (!p["flow"].isNullOrEmpty()) put("flow", p["flow"]) })))))
            "vmess" -> out.put("protocol", "vmess").put("settings", JSONObject().put("vnext", JSONArray().put(
                JSONObject().put("address", s.host).put("port", s.port).put("users", JSONArray().put(
                    JSONObject().put("id", s.secret).put("alterId", (p["aid"] ?: "0").toIntOrNull() ?: 0)
                        .put("security", p["scy"] ?: "auto"))))))
            "trojan" -> out.put("protocol", "trojan").put("settings", JSONObject().put("servers", JSONArray().put(
                JSONObject().put("address", s.host).put("port", s.port).put("password", s.secret))))
            "ss" -> out.put("protocol", "shadowsocks").put("settings", JSONObject().put("servers", JSONArray().put(
                JSONObject().put("address", s.host).put("port", s.port)
                    .put("method", p["method"] ?: "aes-128-gcm").put("password", s.secret))))
        }
        if (s.proto != "ss") out.put("streamSettings", stream(p))

        return JSONObject()
            .put("log", JSONObject().put("loglevel", "warning"))
            .put("dns", JSONObject().put("servers", JSONArray().put("1.1.1.1").put("8.8.8.8")))
            .put("inbounds", JSONArray().put(JSONObject().put("tag", "socks").put("port", SOCKS_PORT)
                .put("listen", "127.0.0.1").put("protocol", "socks")
                .put("settings", JSONObject().put("udp", true))))
            .put("outbounds", JSONArray().put(out)
                .put(JSONObject().put("tag", "direct").put("protocol", "freedom")))
            .toString()
    }

    private fun stream(p: Map<String, String>): JSONObject {
        val net = p["type"]?.ifEmpty { null } ?: "tcp"
        val sec = p["security"]?.ifEmpty { null } ?: "none"
        val st = JSONObject().put("network", net).put("security", sec)
        val sni = p["sni"] ?: ""
        val fp = p["fp"]?.ifEmpty { null } ?: "chrome"
        if (sec == "tls") st.put("tlsSettings", JSONObject().put("serverName", sni).put("fingerprint", fp))
        if (sec == "reality") st.put("realitySettings", JSONObject().put("serverName", sni).put("fingerprint", fp)
            .put("publicKey", p["pbk"] ?: "").put("shortId", p["sid"] ?: "").put("spiderX", p["spx"] ?: ""))
        if (net == "ws") st.put("wsSettings", JSONObject().put("path", p["path"] ?: "/")
            .put("headers", JSONObject().put("Host", p["host"] ?: sni)))
        if (net == "grpc") st.put("grpcSettings", JSONObject().put("serviceName", p["serviceName"] ?: p["path"] ?: ""))
        return st
    }
}
