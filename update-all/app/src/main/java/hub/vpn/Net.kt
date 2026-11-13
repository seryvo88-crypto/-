package hub.vpn

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** Загрузка подписки. На Android 6 нет корневого сертификата Let's Encrypt (ISRG), поэтому он добавлен в приложение. */
object Net {
    fun fetch(ctx: Context, u: String): String {
        val c = URL(u).openConnection() as HttpURLConnection
        if (c is HttpsURLConnection) c.sslSocketFactory = factory(ctx)
        c.connectTimeout = 15000
        c.readTimeout = 15000
        c.setRequestProperty("User-Agent", "PashtetVPN/0.2")
        return c.inputStream.bufferedReader().use { it.readText() }
    }

    private fun manager(ks: KeyStore?): X509TrustManager {
        val f = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        f.init(ks)
        return f.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    private fun factory(ctx: Context): SSLSocketFactory {
        val system = manager(null)
        val ks = KeyStore.getInstance(KeyStore.getDefaultType())
        ks.load(null, null)
        val cf = CertificateFactory.getInstance("X.509")
        var i = 0
        ctx.resources.openRawResource(R.raw.isrg_roots).use { s ->
            for (cert in cf.generateCertificates(s)) ks.setCertificateEntry("isrg" + (i++), cert)
        }
        val extra = manager(ks)
        val both = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, type: String) = system.checkClientTrusted(chain, type)
            override fun checkServerTrusted(chain: Array<X509Certificate>, type: String) {
                try { system.checkServerTrusted(chain, type) }
                catch (e: CertificateException) { extra.checkServerTrusted(chain, type) }
            }
            override fun getAcceptedIssuers(): Array<X509Certificate> = system.acceptedIssuers + extra.acceptedIssuers
        }
        val sc = SSLContext.getInstance("TLS")
        sc.init(null, arrayOf(both), null)
        return sc.socketFactory
    }
}
