package com.reversec.jsolar.api.transport;

public interface SecureTransport {

    public abstract String getHostCertificateFingerprint();
    public abstract String getPeerCertificateFingerprint();
}
