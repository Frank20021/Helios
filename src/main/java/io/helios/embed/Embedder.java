package io.helios.embed;

import io.helios.tokenizer.Token;
import io.helios.tokenizer.Tokenizer;
import io.helios.tokenizer.TokenizerOptions;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.CRC32;

public final class Embedder {
    public static final int DEFAULT_DIM = 384;
    public static final String MODEL_NAME = "hashing-v1";

    private final TokenizerOptions tok;
    private final int dim;

    public Embedder(TokenizerOptions tok, int dim) {
        this.tok = tok == null ? TokenizerOptions.defaults() : tok;
        this.dim = dim < 1 ? DEFAULT_DIM : dim;
    }

    public int dim() {
        return dim;
    }

    public String modelId() {
        return MODEL_NAME + "/" + dim;
    }

    public float[] embed(String text) {
        float[] vec = new float[dim];
        for (Token token : Tokenizer.tokenize(text, tok)) {
            addFeature(vec, "t:" + token.term());
        }
        int[] runes = ngramRunes(text);
        for (int i = 0; i + 3 <= runes.length; i++) {
            addFeature(vec, "g:" + new String(runes, i, 3));
        }
        l2normalize(vec);
        return vec;
    }

    private static void addFeature(float[] vec, String feature) {
        long v = fnv64a(feature.getBytes(StandardCharsets.UTF_8));
        int i = (int) Long.remainderUnsigned(v, vec.length);
        if ((v & 1) == 1) {
            vec[i]++;
        } else {
            vec[i]--;
        }
    }

    public static long fnv64a(byte[] data) {
        long hash = 0xcbf29ce484222325L;
        for (byte b : data) {
            hash ^= (b & 0xff);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static int[] ngramRunes(String text) {
        if (text == null) {
            return new int[0];
        }
        StringBuilder b = new StringBuilder();
        text.toLowerCase().codePoints().forEach(cp -> {
            if (Character.isLetter(cp) || Character.isDigit(cp)) {
                b.appendCodePoint(cp);
            }
        });
        return b.toString().codePoints().toArray();
    }

    private static void l2normalize(float[] vec) {
        double sum = 0;
        for (float v : vec) {
            sum += (double) v * v;
        }
        if (sum == 0) {
            return;
        }
        float inv = (float) (1 / Math.sqrt(sum));
        for (int i = 0; i < vec.length; i++) {
            vec[i] *= inv;
        }
    }

    public static byte[] encode(float[] vec) {
        ByteBuffer buf = ByteBuffer.allocate(4 * vec.length).order(ByteOrder.LITTLE_ENDIAN);
        for (float v : vec) {
            buf.putFloat(v);
        }
        return buf.array();
    }

    public static float[] decode(byte[] raw) {
        if (raw == null || raw.length % 4 != 0) {
            throw new IllegalArgumentException("embedding length " + (raw == null ? 0 : raw.length) + " is not a multiple of 4");
        }
        ByteBuffer buf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        float[] out = new float[raw.length / 4];
        for (int i = 0; i < out.length; i++) {
            out[i] = buf.getFloat();
        }
        return out;
    }

    public static double dot(float[] a, float[] b) {
        int n = Math.min(a.length, b.length);
        double sum = 0;
        for (int i = 0; i < n; i++) {
            sum += (double) a[i] * b[i];
        }
        return sum;
    }

    @SuppressWarnings("unused")
    private static long unusedCrc(byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(data);
        return crc.getValue();
    }
}
