package egg.client.media;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class Artist extends Structure {

    public byte[] title = new byte[512];
    public byte[] artist = new byte[512];
    public byte[] sourceApp = new byte[256];
    public Pointer albumArtPng;
    public int albumArtSize;
    public byte isPlaying;
    public long durationMs;
    public long positionMs;

    public Artist() {
        super(1);
    }

    @Override
    protected List<String> getFieldOrder() {
        return Arrays.asList("title", "artist", "sourceApp", "albumArtPng", "albumArtSize", "isPlaying", "durationMs", "positionMs");
    }

    public String getTitle() {
        return parseString(this.title);
    }

    public String getArtist() {
        return parseString(this.artist);
    }

    public String getSource() {
        return parseString(this.sourceApp);
    }

    private String parseString(byte[] bytes) {
        int len = 0;
        while (len < bytes.length && bytes[len] != 0) {
            len++;
        }
        return new String(bytes, 0, len, StandardCharsets.UTF_8);
    }

    public byte[] getAlbumArt() {
        return (this.albumArtPng != null && this.albumArtSize > 0)
                ? this.albumArtPng.getByteArray(0L, this.albumArtSize)
                : null;
    }

    public boolean isPlaying() {
        return this.isPlaying != 0;
    }
}
