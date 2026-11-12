package io.github.reajason.dubbo.fixture.client.godzilla;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Locale;

/**
 * Client side of the Godzilla transport the plugin's Godzilla shell implements: an
 * MD5-derived key, XOR plus base64 payloads, and a response wrapped in the pass phrase hash.
 */
public final class GodzillaProtocol {

    private static final int HASH_HALF = 16;

    private GodzillaProtocol() {
    }

    public static String transportKey(String configKey) {
        return md5Hex(configKey).substring(0, HASH_HALF);
    }

    public static String passHash(String pass, String transportKey) {
        return md5Hex(pass + transportKey).toUpperCase(Locale.ROOT);
    }

    /**
     * The first request is the payload class itself. The shell defines it, instantiates it on
     * every later request and answers plain {@code ok} this once.
     */
    public static byte[] payloadRequest(String transportKey) throws IOException {
        return encrypt(readPayloadClass(), transportKey);
    }

    public static byte[] commandRequest(String command, String transportKey) {
        return encrypt(command.getBytes(StandardCharsets.UTF_8), transportKey);
    }

    public static String unwrapResponse(byte[] response, String transportKey, String passHash) {
        String text = new String(response, StandardCharsets.UTF_8);
        if ("ok".equals(text)) {
            return text;
        }
        String prefix = passHash.substring(0, HASH_HALF);
        String suffix = passHash.substring(HASH_HALF);
        if (!text.startsWith(prefix) || !text.endsWith(suffix)
                || text.length() < prefix.length() + suffix.length()) {
            throw new IllegalStateException("unexpected godzilla response: " + text);
        }
        String body = text.substring(prefix.length(), text.length() - suffix.length());
        return new String(decrypt(body, transportKey), StandardCharsets.UTF_8);
    }

    private static byte[] encrypt(byte[] data, String transportKey) {
        byte[] keyBytes = transportKey.getBytes(StandardCharsets.UTF_8);
        byte[] xored = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            xored[i] = (byte) (data[i] ^ keyBytes[i % keyBytes.length]);
        }
        return Base64.getEncoder().encodeToString(xored).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] decrypt(String base64Data, String transportKey) {
        byte[] data = Base64.getDecoder().decode(base64Data);
        byte[] keyBytes = transportKey.getBytes(StandardCharsets.UTF_8);
        byte[] plain = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            plain[i] = (byte) (data[i] ^ keyBytes[i % keyBytes.length]);
        }
        return plain;
    }

    private static byte[] readPayloadClass() throws IOException {
        String resource = GodzillaCommandPayload.class.getSimpleName() + ".class";
        try (InputStream input = GodzillaCommandPayload.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("missing " + resource);
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) > 0) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static String md5Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xf, 16));
                hex.append(Character.forDigit(b & 0xf, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 unavailable", e);
        }
    }
}
