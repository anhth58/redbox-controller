package com.redboxsa.controller.update;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

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
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

/**
 * HTTPS for the controller. TLS 1.2 and 1.3 (1.3 on Android 6 comes from Conscrypt, see App).
 * Trusts the system CAs plus the bundled ISRG Root X1, which Android 6 lacks (Let's Encrypt servers such as dev).
 */
public final class Http {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static OkHttpClient client;
    // Server clock from the last response's Date header, anchored to elapsedRealtime (backup time source).
    private static long serverDateMs;
    private static long serverDateElapsed;

    private Http() {
    }

    /** A non-2xx answer; the code tells e.g. a rejected token (401) from a missing endpoint. */
    static final class StatusException extends IOException {
        final int code;

        StatusException(int code, String url) {
            super("HTTP " + code + " " + url);
            this.code = code;
        }
    }

    /** @param token locker token sent as "Authorization: Bearer", or null for the old token-less APIs */
    static JSONObject getJson(Context context, String url, String token) throws IOException, JSONException {
        return json(execute(context, request(url, token).build()), url);
    }

    public static JSONObject postJson(Context context, String url, String token, JSONObject body) throws IOException, JSONException {
        return json(execute(context, request(url, token).post(RequestBody.create(body.toString(), JSON)).build()), url);
    }

    /** Current time according to the server, or 0 if no response in the last hour (or a reboot since). */
    static synchronized long serverNow() {
        long age = SystemClock.elapsedRealtime() - serverDateElapsed;
        return serverDateMs == 0 || age < 0 || age > 60 * 60 * 1000L ? 0 : serverDateMs + age;
    }

    /**
     * Resumable, for lockers on weak SIM connections: a broken download keeps its bytes in {@code target}.part and the next
     * attempt asks only for the rest (Range, with If-Range on the ETag so a changed file starts over). A large apk then
     * costs its size once, however many cycles it takes. A broken download never replaces {@code target}.
     * Each target needs its own name: another url for the same target drops the partial bytes.
     *
     * @throws StatusException the server refused the file (missing, forbidden): retrying will not help
     */
    public static void download(Context context, String url, File target) throws IOException {
        File part = new File(target.getPath() + ".part");
        File meta = new File(target.getPath() + ".part.meta"); // the url and ETag the partial bytes belong to
        String[] saved = readMeta(meta);
        long have = url.equals(saved[0]) && !saved[1].isEmpty() ? part.length() : 0;
        if (have == 0) part.delete();

        // identity: byte offsets must refer to the file itself, never to a gzip stream.
        Request.Builder request = new Request.Builder().url(url).header("Accept-Encoding", "identity");
        if (have > 0) request.header("Range", "bytes=" + have + "-").header("If-Range", saved[1]);
        try (Response response = execute(context, request.build())) {
            if (response.code() == 416) {
                // The partial bytes do not fit the file (e.g. longer): drop them, start over next time.
                part.delete();
                throw new IOException("Cannot resume " + url + ", starting over next time");
            }
            if (!response.isSuccessful()) throw new StatusException(response.code(), url);
            boolean resumed = response.code() == 206;
            if (resumed && !String.valueOf(response.header("Content-Range")).startsWith("bytes " + have + "-")) {
                part.delete();
                throw new IOException("Unexpected Content-Range " + response.header("Content-Range") + " for " + url);
            }
            if (!resumed) have = 0; // 200: the whole file again (no Range support, or the file changed)
            writeMeta(meta, url, response.header("ETag", ""));
            long expected = response.body().contentLength(); // the remaining bytes when resumed
            try (BufferedSink sink = Okio.buffer(resumed ? Okio.appendingSink(part) : Okio.sink(part))) {
                sink.writeAll(response.body().source());
            }
            if (expected >= 0 && part.length() != have + expected) {
                throw new IOException("Incomplete download " + part.length() + "/" + (have + expected) + " " + url);
            }
        }
        if (!part.renameTo(target)) throw new IOException("Cannot move " + part + " to " + target);
        meta.delete();
    }

    /** Same apk again at most once a day: skipped, failed or still-offered apks would otherwise cost SIM data every cycle. */
    public static final long REFETCH_AFTER_MS = 24 * 60 * 60 * 1000L;

    /** @return true when {@code url} was fully fetched less than {@code windowMs} ago (see {@link #markFetched}) */
    public static boolean fetchedRecently(Context context, String url, long windowMs) {
        long at = context.getSharedPreferences("downloads", Context.MODE_PRIVATE).getLong(url, 0);
        long age = System.currentTimeMillis() - at;
        return at != 0 && age >= 0 && age < windowMs; // age < 0: the clock was set back, do not trust it
    }

    public static void markFetched(Context context, String url) {
        SharedPreferences prefs = context.getSharedPreferences("downloads", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        long now = System.currentTimeMillis();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            Object at = entry.getValue();
            if (!(at instanceof Long) || now - (Long) at > REFETCH_AFTER_MS) editor.remove(entry.getKey());
        }
        editor.putLong(url, now).apply();
    }

    private static String[] readMeta(File meta) {
        try (BufferedSource source = Okio.buffer(Okio.source(meta))) {
            String[] lines = source.readUtf8().split("\n", -1);
            return lines.length == 2 ? lines : new String[]{"", ""};
        } catch (IOException e) {
            return new String[]{"", ""};
        }
    }

    private static void writeMeta(File meta, String url, String etag) throws IOException {
        try (BufferedSink sink = Okio.buffer(Okio.sink(meta))) {
            sink.writeUtf8(url + "\n" + etag);
        }
    }

    private static Request.Builder request(String url, String token) {
        Request.Builder builder = new Request.Builder().url(url);
        return token == null ? builder : builder.header("Authorization", "Bearer " + token);
    }

    private static Response execute(Context context, Request request) throws IOException {
        return client(context).newCall(request).execute();
    }

    private static JSONObject json(Response response, String url) throws IOException, JSONException {
        try (Response r = response) {
            if (!r.isSuccessful()) throw new StatusException(r.code(), url);
            JSONObject json = new JSONObject(r.body().string());
            rememberServerDate(r);
            return json;
        }
    }

    /**
     * Only from our own successful API answers: a download may come from a CDN cache whose Date is hours or days old,
     * which would set the locker clock back. "Age" marks such a cached answer.
     */
    private static void rememberServerDate(Response response) {
        Date date = response.headers().getDate("Date");
        if (date == null || response.header("Age") != null) return;
        synchronized (Http.class) {
            serverDateMs = date.getTime();
            serverDateElapsed = SystemClock.elapsedRealtime();
        }
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
