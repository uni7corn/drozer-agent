package com.reversec.jsolar.api.connectors;

import android.util.Log;

import java.io.IOException;
import java.net.ServerSocket;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;

public class ServerSocketFactory {

    private static final String TAG = "ServerSocketFactory";

    public ServerSocket createSocket(Server server) throws CertificateException, IOException, KeyManagementException, KeyStoreException, UnrecoverableKeyException {
        if (server.isSSL())
            return this.createSSLSocket(server);
        else
            return new ServerSocket(server.getPort());
    }

    public SSLServerSocket createSSLSocket(Server server) throws CertificateException, IOException, KeyManagementException, KeyStoreException, UnrecoverableKeyException {
        try {
            KeyManager[] keyManagers = server.getKeyManagers();
            Log.i(TAG, "KeyManagers: " + (keyManagers != null ? keyManagers.length + " entries" : "null"));
            if (keyManagers != null) {
                for (int i = 0; i < keyManagers.length; i++) {
                    Log.i(TAG, "  KeyManager[" + i + "]: " + keyManagers[i].getClass().getName());
                }
            }

            SSLContext context = SSLContext.getInstance("TLS");
            context.init(keyManagers, null, null);

            SSLServerSocket ss = (SSLServerSocket) context.getServerSocketFactory().createServerSocket(server.getPort());

            // Android API 16-19 supports TLSv1.2 but doesn't enable it by default.
            // Explicitly enable all supported TLS protocols (excluding SSLv3) so the
            // Python client (which requires TLSv1.2+) can connect on older devices.
            String[] supported = ss.getSupportedProtocols();
            List<String> protocols = new ArrayList<>();
            for (String p : supported) {
                if (!p.contains("SSL")) {
                    protocols.add(p);
                }
            }
            ss.setEnabledProtocols(protocols.toArray(new String[0]));

            Log.i(TAG, "Enabled protocols: " + Arrays.toString(ss.getEnabledProtocols()));
            Log.i(TAG, "Enabled cipher suites: " + Arrays.toString(ss.getEnabledCipherSuites()));
            return ss;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("no such algorithm TLS");
        }
    }
}
