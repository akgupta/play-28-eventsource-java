package actors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public abstract class ClientConnectionProtocol {

    public static class ClientMessage {
        private final String connectionId;
        private final String data;

        @JsonCreator
        public ClientMessage(@JsonProperty("connectionId") String connectionId,
                             @JsonProperty("data") String data) {
            this.connectionId = connectionId;
            this.data = data;
        }

        public String getConnectionId() {
            return connectionId;
        }

        public String getData() {
            return data;
        }
    }
}
