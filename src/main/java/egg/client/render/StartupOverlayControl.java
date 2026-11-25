package egg.client.render;

public interface StartupOverlayControl {
    boolean egg$skipStartup();

    default float egg$getMenuRevealOpacity() {
        return 1.0f;
    }
}
