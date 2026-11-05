package egg.client.module.minigames.chess;

import java.nio.charset.StandardCharsets;

public class ChessProtocol {
    public static final String PREFIX = "\u200D\u200D\u200B";
    public static final String SUFFIX = "\u200B\uFEFF";
    private static final char ZERO = '\u200B';
    private static final char ONE = '\u200C';

    public record Packet(String type, String data, String sender) {}

    public static String encode(String text) {
        StringBuilder sb = new StringBuilder();
        sb.append(PREFIX);
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
            for (int i = 7; i >= 0; i--) {
                sb.append(((b >> i) & 1) == 1 ? ONE : ZERO);
            }
        }
        sb.append(SUFFIX);
        return sb.toString();
    }

    public static String decode(String message) {
        if (message == null) return null;
        int start = message.indexOf(PREFIX);
        if (start == -1) return null;
        int end = message.indexOf(SUFFIX, start + PREFIX.length());
        if (end == -1) return null;

        String bits = message.substring(start + PREFIX.length(), end);
        if (bits.isEmpty() || bits.length() % 8 != 0) return null;

        byte[] bytes = new byte[bits.length() / 8];
        for (int i = 0; i < bytes.length; i++) {
            int b = 0;
            for (int j = 0; j < 8; j++) {
                char c = bits.charAt(i * 8 + j);
                if (c == ONE) {
                    b |= (1 << (7 - j));
                } else if (c != ZERO) {
                    return null;
                }
            }
            bytes[i] = (byte) b;
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static Packet parse(String chatMessage) {
        String decoded = decode(chatMessage);
        if (decoded == null || decoded.isEmpty()) return null;

        String[] parts = decoded.split(":", 3);
        if (parts.length < 2) return null;

        String type = parts[0];
        String data = parts.length >= 3 ? parts[1] : "";
        String sender = parts.length >= 3 ? parts[2] : parts[1];

        return new Packet(type, data, sender);
    }

    public static String buildInvite(boolean asWhite, String sender) {
        return "." + encode("INV:" + (asWhite ? "W" : "B") + ":" + sender);
    }

    public static String buildAccept(String sender) {
        return "." + encode("ACC::" + sender);
    }

    public static String buildDecline(String sender) {
        return "." + encode("DEC::" + sender);
    }

    public static String buildMove(int fc, int fr, int tc, int tr, ChessPiece promo, String sender) {
        char promoChar = (promo != null && promo != ChessPiece.NONE) ? promo.getNotation().charAt(0) : 'N';
        String moveData = "" + fc + fr + tc + tr + promoChar;
        return "." + encode("MOV:" + moveData + ":" + sender);
    }

    public static String buildResign(String sender) {
        return "." + encode("RES::" + sender);
    }

    public static String buildReset(String sender) {
        return "." + encode("RST::" + sender);
    }

    public static String buildDrawOffer(String sender) {
        return "." + encode("DRW::" + sender);
    }

    public static String buildDrawAccept(String sender) {
        return "." + encode("DAC::" + sender);
    }
}
