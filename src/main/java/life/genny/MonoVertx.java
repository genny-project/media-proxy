package life.genny;

import io.vertx.core.Vertx;

public class MonoVertx {

  private Vertx vertx;

  public void setVertx(Vertx vertx) {
    this.vertx = vertx;
  }

  public Vertx getVertx() {
    return this.vertx;
  }

  private static volatile MonoVertx instance = null;

  public static MonoVertx getInstance() {
    if (instance == null) {
      synchronized (MonoVertx.class) {
        if (instance == null) {
          instance = new MonoVertx();
        }
      }
    }
    return instance;
  }

}

