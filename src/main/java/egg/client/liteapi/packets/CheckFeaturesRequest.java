package egg.client.liteapi.packets;

import java.util.List;
import java.util.UUID;

public final class CheckFeaturesRequest {
    private final String id;
    private final String method;
    private final Payload payload;

    public CheckFeaturesRequest(String client, List<String> features) {
        this.id = UUID.randomUUID().toString();
        this.method = "checkFeatures";
        this.payload = new Payload(client, features);
    }

    public String getId() {
        return id;
    }

    public String getMethod() {
        return method;
    }

    public Payload getPayload() {
        return payload;
    }

    public static class Payload {
        private final String client;
        private final List<String> features;

        public Payload(String client, List<String> features) {
            this.client = client;
            this.features = features;
        }

        public String getClient() {
            return client;
        }

        public List<String> getFeatures() {
            return features;
        }
    }
}
