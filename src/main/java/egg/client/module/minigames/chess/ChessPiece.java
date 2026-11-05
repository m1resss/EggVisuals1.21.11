package egg.client.module.minigames.chess;

public enum ChessPiece {
    NONE(0, ""),
    PAWN(1, "P"),
    KNIGHT(2, "N"),
    BISHOP(3, "B"),
    ROOK(4, "R"),
    QUEEN(5, "Q"),
    KING(6, "K");

    private final int id;
    private final String notation;

    ChessPiece(int id, String notation) {
        this.id = id;
        this.notation = notation;
    }

    public int getId() {
        return id;
    }

    public String getNotation() {
        return notation;
    }

    public static ChessPiece fromId(int id) {
        for (ChessPiece p : values()) {
            if (p.id == id) return p;
        }
        return NONE;
    }

    public static ChessPiece fromChar(char c) {
        return switch (Character.toUpperCase(c)) {
            case 'P' -> PAWN;
            case 'N' -> KNIGHT;
            case 'B' -> BISHOP;
            case 'R' -> ROOK;
            case 'Q' -> QUEEN;
            case 'K' -> KING;
            default -> NONE;
        };
    }
}
