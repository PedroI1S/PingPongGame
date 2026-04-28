package io.github.some_example_name.network;

/**
 * Converts an IPv4 address to/from a compact 6-character room code (base-36).
 *
 * <p>IPv4 is a 32-bit number; base-36 with 6 digits covers 36^6 ≈ 2.17 billion
 * values, which comfortably spans the full unsigned 32-bit range (≈ 4.29 billion —
 * so codes wrap only above 255.255.255.255, i.e. never for valid addresses).</p>
 *
 * <p>Examples:
 * <pre>
 *   192.168.1.5  →  "1Z141F"
 *   127.0.0.1    →  "Z8KFLT"   (local testing)
 *   10.0.0.1     →  "04MD01"   (ZeroTier / Tailscale style)
 * </pre></p>
 */
public final class RoomCode {

    private static final String CHARS  = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int    BASE   = 36;
    public  static final int    LENGTH = 6;

    private RoomCode() {}

    // ── encode ──────────────────────────────────────────────────────────

    /** "192.168.1.5" → "1Z141F" — returns "??????" for malformed input. */
    public static String encode(String ip) {
        try {
            String[] parts = ip.trim().split("\\.");
            if (parts.length != 4) return "??????";
            long value = 0;
            for (String p : parts) {
                int octet = Integer.parseInt(p);
                if (octet < 0 || octet > 255) return "??????";
                value = value * 256 + octet;
            }
            char[] out = new char[LENGTH];
            for (int i = LENGTH - 1; i >= 0; i--) {
                out[i] = CHARS.charAt((int)(value % BASE));
                value /= BASE;
            }
            return new String(out);
        } catch (Exception e) {
            return "??????";
        }
    }

    // ── decode ──────────────────────────────────────────────────────────

    /**
     * "1Z141F" → "192.168.1.5"
     *
     * @return dotted-quad IP string, or {@code null} if the code is invalid.
     */
    public static String decode(String code) {
        if (code == null) return null;
        code = code.toUpperCase().trim();
        if (code.length() != LENGTH) return null;
        long value = 0;
        for (char c : code.toCharArray()) {
            int idx = CHARS.indexOf(c);
            if (idx < 0) return null;
            value = value * BASE + idx;
        }
        return ((value >> 24) & 0xFF) + "."
             + ((value >> 16) & 0xFF) + "."
             + ((value >>  8) & 0xFF) + "."
             + ( value        & 0xFF);
    }

    // ── helpers ─────────────────────────────────────────────────────────

    /** True if every character is a valid base-36 digit (0-9, A-Z). */
    public static boolean isValidChar(char c) {
        return CHARS.indexOf(Character.toUpperCase(c)) >= 0;
    }
}
