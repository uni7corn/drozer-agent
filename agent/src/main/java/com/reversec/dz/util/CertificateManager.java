package com.reversec.dz.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.reversec.dz.Agent;
import com.reversec.dz.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;

public class CertificateManager {

    private static final String TAG = "CertificateManager";
    private static final String KS_FILENAME = "drozer-server.p12";
    // Bump this to force key regeneration when the generation logic changes.
    private static final int CERT_VERSION = 4;
    private static final String PREF_CERT_VERSION = "cert:version";

    /**
     * Ensures a server certificate exists as a PKCS12 file in app-private storage.
     * Generates a fresh RSA-2048 key + self-signed X.509 v1 cert if missing or outdated.
     */
    public static void ensureCertificateGenerated(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(
                    context.getPackageName() + "_preferences", Context.MODE_MULTI_PROCESS);
            int storedVersion = prefs.getInt(PREF_CERT_VERSION, 0);
            File ksFile = new File(context.getFilesDir(), KS_FILENAME);

            if (ksFile.exists() && storedVersion >= CERT_VERSION) {
                Log.i(TAG, "Certificate already exists (v" + storedVersion + ")");
                return;
            }

            if (ksFile.exists()) {
                Log.i(TAG, "Deleting outdated certificate (v" + storedVersion + " < v" + CERT_VERSION + ")");
                ksFile.delete();
            }

            KeyStore ks = SelfSignedCertGenerator.generate();
            try (FileOutputStream fos = new FileOutputStream(ksFile)) {
                ks.store(fos, SelfSignedCertGenerator.STORE_PASS);
            }

            prefs.edit().putInt(PREF_CERT_VERSION, CERT_VERSION).apply();
            Log.i(TAG, "Generated new server certificate (v" + CERT_VERSION + ")");
        } catch (Exception e) {
            Log.e(TAG, "Failed to generate certificate", e);
        }
    }

    /**
     * Returns KeyManagers backed by the generated PKCS12 keystore, or by the
     * legacy static BKS file as a last-resort fallback.
     */
    public static KeyManager[] getKeyManagers(Context context) {
        try {
            File ksFile = new File(context.getFilesDir(), KS_FILENAME);
            if (ksFile.exists()) {
                KeyStore ks = KeyStore.getInstance("PKCS12");
                try (FileInputStream fis = new FileInputStream(ksFile)) {
                    ks.load(fis, SelfSignedCertGenerator.STORE_PASS);
                }
                KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                        KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(ks, SelfSignedCertGenerator.STORE_PASS);
                return kmf.getKeyManagers();
            }

            // Legacy fallback: static BKS shipped with the app
            return getKeyManagersFromBks(context);
        } catch (Exception e) {
            Log.e(TAG, "Failed to get KeyManagers", e);
            return null;
        }
    }

    private static KeyManager[] getKeyManagersFromBks(Context context) throws Exception {
        // The app ships agent.bks (see Agent.DEFAULT_KEYSTORE), copied to filesDir on startup.
        File bksFile = new File(context.getFilesDir(), Agent.DEFAULT_KEYSTORE);
        if (!bksFile.exists()) return null;

        KeyStore ks = KeyStore.getInstance("BKS");
        try (FileInputStream fis = new FileInputStream(bksFile)) {
            ks.load(fis, "drozer".toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, "drozer".toCharArray());
        return kmf.getKeyManagers();
    }

    /**
     * Returns the SHA-256 fingerprint of the server certificate as a colon-separated
     * hex string, e.g. "AB:CD:12:34:...".
     */
    public static String getCertificateFingerprint(Context context) {
        try {
            byte[] fingerprintBytes = getFingerprintBytes(context);
            if (fingerprintBytes == null) return null;

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < fingerprintBytes.length; i++) {
                if (i > 0) sb.append(':');
                sb.append(String.format("%02X", fingerprintBytes[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "Failed to get certificate fingerprint", e);
            return null;
        }
    }

    /**
     * Returns a human-readable security phrase derived from the certificate fingerprint.
     */
    public static String getSecurityPhrase(Context context) {
        try {
            byte[] fingerprintBytes = getFingerprintBytes(context);
            if (fingerprintBytes == null || fingerprintBytes.length < 6) return null;

            String[] words = loadWordlist(context);
            if (words == null || words.length == 0) return null;

            StringBuilder phrase = new StringBuilder();
            for (int i = 0; i < 3; i++) {
                int hi = fingerprintBytes[i * 2] & 0xFF;
                int lo = fingerprintBytes[i * 2 + 1] & 0xFF;
                int index = ((hi << 8) | lo) % words.length;
                if (i > 0) phrase.append('-');
                phrase.append(words[index]);
            }
            return phrase.toString();
        } catch (Exception e) {
            Log.e(TAG, "Failed to get security phrase", e);
            return null;
        }
    }

    private static byte[] getFingerprintBytes(Context context) throws Exception {
        File ksFile = new File(context.getFilesDir(), KS_FILENAME);
        if (!ksFile.exists()) return null;

        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(ksFile)) {
            ks.load(fis, SelfSignedCertGenerator.STORE_PASS);
        }

        Certificate cert = ks.getCertificate(SelfSignedCertGenerator.ALIAS);
        if (cert == null) return null;

        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return md.digest(cert.getEncoded());
    }

    private static String[] loadWordlist(Context context) {
        List<String> words = new ArrayList<>();
        try (InputStream is = context.getResources().openRawResource(R.raw.wordlist);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    words.add(line);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to load wordlist", e);
            return null;
        }
        return words.toArray(new String[0]);
    }
}
