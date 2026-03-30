package com.reversec.jsolar.connection;

public interface SecureConnection {

    public String getHostCertificateFingerprint();
    public String getPeerCertificateFingerprint();

}