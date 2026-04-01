package com.reversec.jsolar.api.links;

import com.reversec.jsolar.api.DeviceInfo;
import com.reversec.jsolar.api.connectors.Connector;
import com.reversec.jsolar.api.connectors.ServerSocketFactory;
import com.reversec.jsolar.api.transport.SocketTransport;
import com.reversec.jsolar.connection.SecureConnection;
import com.reversec.jsolar.logger.LogMessage;

import android.util.Log;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLServerSocket;

public class Server extends Link{

    private ServerSocket serverSocket = null;

    public Server(com.reversec.jsolar.api.connectors.Server parameters, DeviceInfo deviceInfo) {
        super(parameters, deviceInfo);
    }

    @Override
    public boolean checkForLiveness() {
        return false;
    }

    @Override
    public boolean dieWithLastSession() {
        return true;
    }

    public String getHostCertificateFingerprint() {
        return ((SecureConnection)this.connection).getHostCertificateFingerprint();
    }

    public String getPeerCertificateFingerprint() {
        return ((SecureConnection)this.connection).getPeerCertificateFingerprint();
    }

    @Override
    public boolean mustBind() {
        return false;
    }

    @Override
    public void resetConnection() {
        this.parameters.setStatus(Connector.Status.CONNECTING);

        Thread.yield();
        if(this.serverSocket != null) {
            try {
                this.serverSocket.close();
                this.serverSocket = null;
            } catch (IOException e) {
                this.log(LogMessage.DEBUG, "Failed to resetConnection: " + e.getMessage());
            }
            super.resetConnection();
        }
    }

    @Override
    public void run() {
        // Guard against the race where stopConnector() was called before run() started.
        if (this.stopRequested) {
            this.parameters.setStatus(Connector.Status.OFFLINE);
            return;
        }
        this.running = true;

        this.log(LogMessage.INFO, "Starting Server..." );
        while(this.running) {
            try {
                if (this.connection == null) {
                    this.parameters.setStatus(Connector.Status.CONNECTING);

                    this.log(LogMessage.INFO, "Attempting to bind to port " + ((com.reversec.jsolar.api.connectors.Server) this.parameters).getPort() + "...");
                    this.serverSocket = new ServerSocketFactory().createSocket((com.reversec.jsolar.api.connectors.Server) this.parameters);

                    this.log(LogMessage.INFO, "Waiting for connections...");
                    Socket socket;
                    try {
                        socket = this.serverSocket.accept();
                    } catch (SSLHandshakeException e) {
                        Log.e("Server", "TLS handshake failed with client", e);
                        this.log(LogMessage.ERROR, "TLS handshake failed: " + e.getMessage());
                        continue;
                    }

                    if (socket != null) {
                        this.parameters.setStatus(com.reversec.jsolar.api.connectors.Server.Status.ONLINE);

                        this.log(LogMessage.INFO, "Accepted connection...");

                        this.log(LogMessage.INFO, "Starting drozer thread...");
                        this.createConnection(new SocketTransport(socket));
                    }
                } else {
                    //This code here will totally not create any race conditions...
                    synchronized (this.connection) {
                        try {
                            this.connection.wait();
                        } catch (InterruptedException | IllegalMonitorStateException e) {
                            this.log(LogMessage.DEBUG, "Most likely hit some race condition in links/Server.java");
                        }
                    }
                    // Block until connection == null or connection.started && !connection.running
                    if (this.connection.started && !this.connection.running) {
                        this.log(LogMessage.WARN, "Connection was reset.");

                        this.resetConnection();
                    }

                }
            }
            catch(CertificateException e) {
                this.log(LogMessage.ERROR, "Error loading key material for SSL.");

                this.stopConnector();
            }
            catch(IOException e) {
                // If running was set to false by stopConnector(), the socket close caused
                // this exception intentionally — exit cleanly without resetting.
                if (!this.running) break;
                this.log(LogMessage.ERROR, "IO Error. Resetting connection.");
                this.resetConnection();
            }
            catch(KeyManagementException e) {
                this.log(LogMessage.ERROR, "Error loading key material for SSL.");

                this.stopConnector();
            }
            catch(KeyStoreException e) {
                this.log(LogMessage.ERROR, "Error loading key material for SSL.");

                this.stopConnector();
            }
            catch(UnrecoverableKeyException e) {
                this.log(LogMessage.ERROR, "Error loading key material for SSL.");

                this.stopConnector();
            }
        }
        this.log(LogMessage.INFO, "Stopped.");
        this.parameters.setStatus(Connector.Status.OFFLINE);
    }

    @Override
    public void setStatus(Connector.Status status) {
        this.parameters.setStatus(status);
    }

    public void stopConnector() {
        super.stopConnector();

        try {
            if(this.serverSocket != null) {
                this.serverSocket.close();
                this.serverSocket = null;
            }
        }
        catch(IOException e) {
            this.log(LogMessage.DEBUG, "Issue in stopping connector: " + e);
        }
    }
}
