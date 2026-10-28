package delta.optmedia;

import com.sun.jna.win32.StdCallLibrary;

public interface MediaControlLibrary extends StdCallLibrary {
    boolean GetCurrentMediaInfo(Artist artist);
    void FreeMediaInfo(Artist artist);
    boolean Play();
    boolean Pause();
    boolean TogglePlayPause();
    boolean SkipNext();
    boolean SkipPrevious();
    void SeekToMs(long positionMs);
    float GetVolume();
    boolean SetVolume(float volume);
    void Deinitialize();
}
