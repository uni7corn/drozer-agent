package com.reversec.dz.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 * Generates a self-signed RSA-2048 / SHA256withRSA X.509 v1 certificate
 * using only standard Java crypto APIs. No BouncyCastle, no Android Keystore.
 */
class SelfSignedCertGenerator {

    static final String ALIAS = "drozer-server";
    static final char[] STORE_PASS = "drozer".toCharArray();

    // sha256WithRSAEncryption = 1.2.840.113549.1.1.11
    private static final byte[] OID_SHA256_WITH_RSA = {
            0x06, 0x09,
            0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x0b
    };
    // commonName = 2.5.4.3
    private static final byte[] OID_COMMON_NAME = {
            0x06, 0x03, 0x55, 0x04, 0x03
    };

    static KeyStore generate() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair keyPair = kpg.generateKeyPair();

        Calendar cal = Calendar.getInstance();
        Date notBefore = cal.getTime();
        cal.add(Calendar.YEAR, 10);
        Date notAfter = cal.getTime();

        byte[] tbs = buildTbsCertificate(keyPair, BigInteger.ONE, notBefore, notAfter);

        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(keyPair.getPrivate());
        sig.update(tbs);
        byte[] signature = sig.sign();

        byte[] certDer = derSequence(concat(
                tbs,
                derSequence(concat(OID_SHA256_WITH_RSA, derNull())),
                derBitString(signature)));

        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) cf.generateCertificate(
                new ByteArrayInputStream(certDer));

        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setKeyEntry(ALIAS, keyPair.getPrivate(), STORE_PASS,
                new java.security.cert.Certificate[]{cert});
        return ks;
    }

    private static byte[] buildTbsCertificate(KeyPair keyPair, BigInteger serial,
                                               Date notBefore, Date notAfter) throws Exception {
        byte[] nameDer = buildName("drozer-agent");
        return derSequence(concat(
                derInteger(serial.toByteArray()),
                derSequence(concat(OID_SHA256_WITH_RSA, derNull())),
                nameDer,
                derSequence(concat(derUtcTime(notBefore), derUtcTime(notAfter))),
                nameDer,
                keyPair.getPublic().getEncoded()));
    }

    private static byte[] buildName(String cn) throws Exception {
        byte[] cnValue = derTagged(0x0C, cn.getBytes("UTF-8"));
        byte[] atv = derSequence(concat(OID_COMMON_NAME, cnValue));
        byte[] rdn = derTagged(0x31, atv);
        return derSequence(rdn);
    }

    // -- DER primitives --

    private static byte[] derTagged(int tag, byte[] content) {
        byte[] len = derLength(content.length);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        out.write(len, 0, len.length);
        out.write(content, 0, content.length);
        return out.toByteArray();
    }

    private static byte[] derSequence(byte[] content) { return derTagged(0x30, content); }

    private static byte[] derInteger(byte[] value) { return derTagged(0x02, value); }

    private static byte[] derNull() { return new byte[]{0x05, 0x00}; }

    private static byte[] derBitString(byte[] value) {
        byte[] content = new byte[value.length + 1];
        content[0] = 0x00;
        System.arraycopy(value, 0, content, 1, value.length);
        return derTagged(0x03, content);
    }

    private static byte[] derUtcTime(Date date) throws Exception {
        SimpleDateFormat sdf = new SimpleDateFormat("yyMMddHHmmss");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return derTagged(0x17, (sdf.format(date) + "Z").getBytes("ASCII"));
    }

    private static byte[] derLength(int len) {
        if (len < 0x80) return new byte[]{(byte) len};
        if (len < 0x100) return new byte[]{(byte) 0x81, (byte) len};
        if (len < 0x10000) return new byte[]{(byte) 0x82, (byte) (len >> 8), (byte) len};
        return new byte[]{(byte) 0x83, (byte) (len >> 16), (byte) (len >> 8), (byte) len};
    }

    private static byte[] concat(byte[]... parts) {
        int total = 0;
        for (byte[] p : parts) total += p.length;
        byte[] out = new byte[total];
        int pos = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, pos, p.length);
            pos += p.length;
        }
        return out;
    }
}
