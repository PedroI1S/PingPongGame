package io.github.some_example_name.network;

/**
 * Converts an IPv4 address to/from a compact 7-character room code (base-36).
 *
 * <p>IPv4 is a 32-bit unsigned number (max 4 294 967 295).
 * base-36 with 7 digits covers up to 36^7 − 1 ≈ 78 billion, so every valid
 * IPv4 address fits without overflow — including 192.168.x.x home LANs.</p>
 *
 * <p>Examples:
 * <pre>
 *   192.168.1.5  →  "1HGE139"
 *   127.0.0.1    →  "0Z8KFLT"   (local testing)
 *   10.0.0.1     →  "02RVXTT"   (ZeroTier / Tailscale style)
 * </pre></p>
 */
public final class RoomCode {

    private static final String CHARS  = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int    BASE   = 36;
    public  static final int    LENGTH = 7;

    private RoomCode() {}

    // ── encode ──────────────────────────────────────────────────────────

    /** Sentinel for malformed input — same length as a real code. */
    private static final String INVALID = "?".repeat(LENGTH);

    /** "192.168.1.5" → "1HGE139" — returns {@link #INVALID} for malformed input. */
    public static String encode(String ip) {
        try {
            String[] parts = ip.trim().split("\\.");
            if (parts.length != 4) return INVALID;
            long value = 0;
            for (String p : parts) {
                int octet = Integer.parseInt(p);
                if (octet < 0 || octet > 255) return INVALID;
                value = value * 256 + octet;
            }
            char[] out = new char[LENGTH];
            for (int i = LENGTH - 1; i >= 0; i--) {
                out[i] = CHARS.charAt((int)(value % BASE));
                value /= BASE;
            }
            return new String(out);
        } catch (Exception e) {
            return INVALID;
        }
    }

    // ── decode ──────────────────────────────────────────────────────────

    /**
     * "1HGE139" → "192.168.1.5"
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
