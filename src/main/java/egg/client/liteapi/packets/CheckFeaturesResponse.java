package egg.client.liteapi.packets;

import java.util.Collections;
import java.util.List;

public final class CheckFeaturesResponse {
    private String id;
    private boolean ok;
    private String error;
    private String message;
    private Payload payload;

    public String getId() {
        return id;
    }

    public boolean isOk() {
        return ok;
    }

    public String getError() {
        return error;
    }

    public String getMessage() {
        return message;
    }

    public List<String> getBlocklist() {
        if (payload != null && payload.blocklist != null) {
            return payload.blocklist;
        }
        return Collections.emptyList();
    }

    public static class Payload {
        private List<String> blocklist;

        public List<String> getBlocklist() {
            return blocklist;
        }
    }
}
