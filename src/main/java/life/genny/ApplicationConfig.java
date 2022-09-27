package life.genny;

import io.vertx.core.json.JsonObject;


public class ApplicationConfig {

    private static JsonObject config;

    public static JsonObject getConfig() {
        return config;
    }

    public static void setConfig(JsonObject config) {
        ApplicationConfig.config = config;
    }
}
