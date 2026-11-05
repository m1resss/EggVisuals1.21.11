package egg.client.module.minigames.chess;

public class ChessMove {
    private final int fromCol;
    private final int fromRow;
    private final int toCol;
    private final int toRow;
    private final ChessPiece piece;
    private final ChessPiece capturedPiece;
    private final ChessPiece promoPiece;
    private final boolean isCastling;
    private final boolean isEnPassant;
    private boolean isCheck;
    private boolean isCheckmate;

    public ChessMove(int fromCol, int fromRow, int toCol, int toRow, ChessPiece piece, ChessPiece capturedPiece, ChessPiece promoPiece, boolean isCastling, boolean isEnPassant) {
        this.fromCol = fromCol;
        this.fromRow = fromRow;
        this.toCol = toCol;
        this.toRow = toRow;
        this.piece = piece;
        this.capturedPiece = capturedPiece;
        this.promoPiece = promoPiece;
        this.isCastling = isCastling;
        this.isEnPassant = isEnPassant;
    }

    public int getFromCol() {
        return fromCol;
    }

    public int getFromRow() {
        return fromRow;
    }

    public int getToCol() {
        return toCol;
    }

    public int getToRow() {
        return toRow;
    }

    public ChessPiece getPiece() {
        return piece;
    }

    public ChessPiece getCapturedPiece() {
        return capturedPiece;
    }

    public ChessPiece getPromoPiece() {
        return promoPiece;
    }

    public boolean isCastling() {
        return isCastling;
    }

    public boolean isEnPassant() {
        return isEnPassant;
    }

    public boolean isCheck() {
        return isCheck;
    }

    public void setCheck(boolean check) {
        this.isCheck = check;
    }

    public boolean isCheckmate() {
        return isCheckmate;
    }

    public void setCheckmate(boolean checkmate) {
        this.isCheckmate = checkmate;
    }

    public String getNotation() {
        if (isCastling) {
            return toCol > fromCol ? "O-O" : "O-O-O";
        }

        StringBuilder sb = new StringBuilder();
        if (piece != ChessPiece.PAWN) {
            sb.append(piece.getNotation());
        }

        sb.append((char) ('a' + fromCol));
        sb.append(8 - fromRow);

        sb.append(capturedPiece != ChessPiece.NONE || isEnPassant ? "x" : "-");

        sb.append((char) ('a' + toCol));
        sb.append(8 - toRow);

        if (promoPiece != ChessPiece.NONE && promoPiece != null) {
            sb.append("=").append(promoPiece.getNotation());
        }

        if (isCheckmate) {
            sb.append("#");
        } else if (isCheck) {
            sb.append("+");
        }

        return sb.toString();
    }
}
