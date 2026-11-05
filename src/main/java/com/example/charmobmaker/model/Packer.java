package com.example.charmobmaker.model;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

// Squeezes design text for the network and cuts it into pieces small enough for one packet.
public final class Packer {
    private Packer() {}

    public static byte[] pack(String json) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(json.length() / 3 + 32);
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }

    // Never unpacks more than the limit, so a tiny hostile upload can't balloon into gigabytes. Null = not valid or too big.
    public static String unpack(byte[] data) {
        if (data == null || data.length > Limits.MAX_DESIGN_GZ_BYTES) return null;
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(data))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(data.length * 4, 64 * 1024));
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                if (out.size() + n > Limits.MAX_JSON_BYTES) return null;
                out.write(buf, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    public static List<byte[]> split(byte[] data, int size) {
        List<byte[]> out = new ArrayList<>();
        for (int i = 0; i < data.length; i += size) out.add(Arrays.copyOfRange(data, i, Math.min(data.length, i + size)));
        if (out.isEmpty()) out.add(new byte[0]);
        return out;
    }
}
