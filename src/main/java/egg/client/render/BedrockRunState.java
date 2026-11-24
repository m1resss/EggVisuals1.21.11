package egg.client.render;

public interface BedrockRunState {
    static float strideScale(Object state) {
        return state instanceof BedrockRunState running && running.egg$isBedrockRunning() ? 0.13f : 1f;
    }

    boolean egg$isBedrockRunning();

    void egg$setBedrockRunning(boolean running);
}
