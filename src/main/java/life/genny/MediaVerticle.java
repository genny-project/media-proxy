package life.genny;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.Logger;

public class MediaVerticle extends AbstractVerticle {

    protected static final Logger log = org.apache.logging.log4j.LogManager.getLogger(MediaVerticle.class);
    @Override
    public void start() {
        MonoVertx.getInstance().setVertx(vertx);
        Server.run();
    }

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new MediaVerticle(), new DeploymentOptions()
                .setWorker(false)
                .setInstances(1)
                .setWorkerPoolSize(40) // Default is 20
        );

    }

}
