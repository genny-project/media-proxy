package life.genny;

import io.vertx.rxjava.core.AbstractVerticle;

public class MediaVerticle extends AbstractVerticle {
  
  @Override
  public void start() {
    MonoVertx.getInstance().setVertx(vertx);
    Server.run();
  }

}
