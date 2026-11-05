package egg.client.module.minigames.chess;

import egg.client.render.IconRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

import java.util.EnumMap;
import java.util.Map;

public final class ChessPieceRenderer {
    private ChessPieceRenderer() {}

    private static final Map<ChessPiece, Identifier> WHITE_TEXTURES = new EnumMap<>(ChessPiece.class);
    private static final Map<ChessPiece, Identifier> BLACK_TEXTURES = new EnumMap<>(ChessPiece.class);

    static {
        WHITE_TEXTURES.put(ChessPiece.PAWN, Identifier.of("egg", "textures/chess/w_pawn.png"));
        WHITE_TEXTURES.put(ChessPiece.KNIGHT, Identifier.of("egg", "textures/chess/w_knight.png"));
        WHITE_TEXTURES.put(ChessPiece.BISHOP, Identifier.of("egg", "textures/chess/w_bishop.png"));
        WHITE_TEXTURES.put(ChessPiece.ROOK, Identifier.of("egg", "textures/chess/w_rook.png"));
        WHITE_TEXTURES.put(ChessPiece.QUEEN, Identifier.of("egg", "textures/chess/w_queen.png"));
        WHITE_TEXTURES.put(ChessPiece.KING, Identifier.of("egg", "textures/chess/w_king.png"));

        BLACK_TEXTURES.put(ChessPiece.PAWN, Identifier.of("egg", "textures/chess/b_pawn.png"));
        BLACK_TEXTURES.put(ChessPiece.KNIGHT, Identifier.of("egg", "textures/chess/b_knight.png"));
        BLACK_TEXTURES.put(ChessPiece.BISHOP, Identifier.of("egg", "textures/chess/b_bishop.png"));
        BLACK_TEXTURES.put(ChessPiece.ROOK, Identifier.of("egg", "textures/chess/b_rook.png"));
        BLACK_TEXTURES.put(ChessPiece.QUEEN, Identifier.of("egg", "textures/chess/b_queen.png"));
        BLACK_TEXTURES.put(ChessPiece.KING, Identifier.of("egg", "textures/chess/b_king.png"));
    }

    public static void drawPiece(DrawContext context, ChessPiece piece, int color, float x, float y, float size, float alpha) {
        if (piece == ChessPiece.NONE || color == ChessBoard.COLOR_NONE) return;

        Identifier tex = (color == ChessBoard.COLOR_WHITE) ? WHITE_TEXTURES.get(piece) : BLACK_TEXTURES.get(piece);
        if (tex == null) return;

        int tint = ((int) (alpha * 255.0f) << 24) | 0xFFFFFF;
        IconRenderer.drawTextureScaled(context, tex, x, y, size, size, 128, 128, tint);
    }
}
