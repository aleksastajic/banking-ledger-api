package com.aleksastajic.ledger.ledger.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class Sha256 {

    private Sha256() {
    }

    public static String hex(String input) {
        return hex(input.getBytes(StandardCharsets.UTF_8));
    }

    public static String hex(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] out = digest.digest(input);
            return toHex(out);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        int i = 0;
        for (byte b : bytes) {
            int v = b & 0xFF;
            out[i++] = Character.forDigit(v >>> 4, 16);
            out[i++] = Character.forDigit(v & 0x0F, 16);
        }
        return new String(out);
    }
}
