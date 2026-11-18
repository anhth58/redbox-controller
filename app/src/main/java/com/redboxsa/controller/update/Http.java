package com.redboxsa.controller.update;

import android.content.Context;

import com.redboxsa.controller.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;

/**
 * HTTPS for the controller. TLS 1.2 and 1.3 (1.3 on Android 6 comes from Conscrypt, see App).
 * Trusts the system CAs plus the bundled ISRG Root X1, which Android 6 lacks (Let's Encrypt servers such as dev).
 */
final class Http {

    private static OkHttpClient client;

    private Http() {
    }

    static JSONObject getJson(Context context, String url) throws IOException, JSONException {
        try (Response response = client(context).newCall(new Request.Builder().url(url).build()).execute()) {
            if (!response.isSuccessful()) throw new IOException("HTTP " + response.code() + " " + url);
            return new JSONObject(response.body().string());
        }
    }

    /** Writes to a temp file first so a broken download never replaces {@code target}. */
    static void download(Context context, String url, File target) throws IOException {
        File tmp = new File(target.getPath() + ".part");
        try (Response response = client(context).newCall(new Request.Builder().url(url).build()).execute()) {
            if (!response.isSuccessful()) throw new IOException("HTTP " + response.code() + " " + url);
            long expected = response.body().contentLength();
            try (BufferedSink sink = Okio.buffer(Okio.sink(tmp))) {
                sink.writeAll(response.body().source());
            }
            if (expected >= 0 && tmp.length() != expected) {
                throw new IOException("Incomplete download " + tmp.length() + "/" + expected + " " + url);
            }
        } catch (IOException e) {
            tmp.delete();
            throw e;
        }
        if (!tmp.renameTo(target)) throw new IOException("Cannot move " + tmp + " to " + target);
    }

    private static synchronized OkHttpClient client(Context context) throws IOException {
        if (client == null) {
            try {
                X509TrustManager trust = trustManager(context);
                SSLContext tls = SSLContext.getInstance("TLS");
                tls.init(null, new TrustManager[]{trust}, null);
                client = new OkHttpClient.Builder()
                        .sslSocketFactory(tls.getSocketFactory(), trust)
                        .connectTimeout(20, TimeUnit.SECONDS)
                        .readTimeout(60, TimeUnit.SECONDS)
                        .build();
            } catch (GeneralSecurityException e) {
                throw new IOException("TLS setup failed", e);
            }
        }
        return client;
    }

    private static X509TrustManager trustManager(Context context) throws GeneralSecurityException, IOException {
        X509TrustManager system = defaultTrustManager(null);
        KeyStore bundled = KeyStore.getInstance(KeyStore.getDefaultType());
        bundled.load(null, null);
        try (InputStream in = context.getResources().openRawResource(R.raw.isrg_root_x1)) {
            bundled.setCertificateEntry("isrg_root_x1", CertificateFactory.getInstance("X.509").generateCertificate(in));
        }
        X509TrustManager extra = defaultTrustManager(bundled);

        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                system.checkClientTrusted(chain, authType);
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                try {
                    system.checkServerTrusted(chain, authType);
                } catch (CertificateException e) {
                    extra.checkServerTrusted(chain, authType);
                }
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                X509Certificate[] a = system.getAcceptedIssuers();
                X509Certificate[] b = extra.getAcceptedIssuers();
                X509Certificate[] all = new X509Certificate[a.length + b.length];
                System.arraycopy(a, 0, all, 0, a.length);
                System.arraycopy(b, 0, all, a.length, b.length);
                return all;
            }
        };
    }

    /** @param keyStore null = the system CA store */
    private static X509TrustManager defaultTrustManager(KeyStore keyStore) throws GeneralSecurityException {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(keyStore);
        for (TrustManager tm : factory.getTrustManagers()) {
            if (tm instanceof X509TrustManager) return (X509TrustManager) tm;
        }
        throw new GeneralSecurityException("No X509TrustManager");
    }
}
