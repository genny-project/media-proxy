package life.genny;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MediaVerticle extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(MediaVerticle.class);
    @Override
    public void start() {
        MonoVertx.getInstance().setVertx(vertx);
        Server.run();
    }

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new MediaVerticle(), new DeploymentOptions()
                .setWorker(true)
                .setInstances(1)
                .setWorkerPoolSize(100) // Default is 20
        );

    }

}
