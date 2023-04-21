package life.genny;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.apache.tika.Tika;

import io.minio.ObjectStat;
import io.vertx.core.http.Http2Settings;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.core.buffer.Buffer;
import io.vertx.rxjava.core.http.HttpServerResponse;
import io.vertx.rxjava.ext.web.FileUpload;
import io.vertx.rxjava.ext.web.Router;
import io.vertx.rxjava.ext.web.RoutingContext;
import io.vertx.rxjava.ext.web.handler.BodyHandler;
import io.vertx.rxjava.ext.web.handler.CorsHandler;
import io.vertx.rxjava.ext.web.handler.StaticHandler;
import life.genny.qwandautils.JsonUtils;
import life.genny.security.TokenIntrospection;
public class Server {

  private final static int serverPort;

  private final static String APPLICATION_X_MATROSKA = "application/x-matroska";
  private final static int CHUNK_SIZE = 1000000;

  static {
    serverPort =
        Optional.ofNullable(System.getenv("MEDIA_PROXY_SERVER_PORT"))
            .map(Integer::valueOf).orElse(8080);
  }

  private static final String X_PINGARUNER = "X-PINGARUNER";
  private static final String X_REQUESTED_WITH = "X-Requested-With";
  private static final String X_TOTAL_COUNT = "X-Total-Count";
  private static final String CONTENT_TYPE = "Content-Type";
  private static final String CONNECTION = "Connection";
  private static final String ACCESS_CONTROL_ALLOW_ORIGIN =
      "Access-Control-Allow-Origin";
  private static final String CONTENT_RANGE = "Content-Range";

  public static CorsHandler corsHandler() {
    return CorsHandler.create("*").allowedHeader("*")
        .allowedMethod(HttpMethod.GET)
        .allowedMethod(HttpMethod.POST)
        .allowedMethod(HttpMethod.DELETE)
        .allowedMethod(HttpMethod.OPTIONS).allowedHeader(X_PINGARUNER)
        .allowedHeader(CONTENT_TYPE).allowedHeader(X_REQUESTED_WITH)
        .allowedHeader(ACCESS_CONTROL_ALLOW_ORIGIN)
        .allowedHeader(X_TOTAL_COUNT)
        .allowedHeader(CONNECTION)
        .exposedHeader(CONTENT_RANGE);
  }

  public static void run() {
    Vertx vertx = Vertx.newInstance(MonoVertx.getInstance().getVertx());
    Router router = Router.router(vertx);
    router.route().handler(corsHandler());
    /*
     * setBodyLimit expected a long that defines the number of bytes so a file of 100 kilobytes should
     * be written as 100000L
     */
    router.route().handler(BodyHandler.create()
        .setDeleteUploadedFilesOnEnd(true));

    router.route(HttpMethod.POST, "/media")
        .blockingHandler(Server::userFileUploadHandler);

    router.route(HttpMethod.GET, "/media/:fileuuid")
        .blockingHandler(Server::userFindFileHandler);

    router.route(HttpMethod.POST, "/public")
        .blockingHandler(Server::publicFileUploadHandler);

    router.route(HttpMethod.GET, "/public/:fileuuid")
        .blockingHandler(Server::publicFindFileHandler);

    router.route(HttpMethod.GET, "/public/:fileuuid/name")
        .blockingHandler(Server::publicFindFileNameHandler);

    router.route(HttpMethod.GET, "/public/video/:fileuuid")
        .blockingHandler(Server::publicFindVideoHandler);
    
    router.route(HttpMethod.HEAD, "/public/:fileuuid")
        .blockingHandler(Server::fileSize);

    router.route(HttpMethod.DELETE, "/public/:fileuuid")
        .blockingHandler(Server::publicDeleteFileHandler);
    vertx.createHttpServer().requestHandler(router::accept).listen(serverPort);
  }


  public static void userFileUploadHandler(RoutingContext ctx) {
    List<String> roles = TokenIntrospection.setRoles("user");
    String tokenFromHeader = Minio.getTokenFromHeader(ctx);
    Boolean isAllowed = TokenIntrospection.checkAuthForRoles(MonoVertx.getInstance().getVertx(), roles, tokenFromHeader);
    if(!isAllowed){
      ctx.response().setStatusCode(401).end();
    }
    else {
      Set<FileUpload> fileUploads = ctx.fileUploads();
      UUID userUUID = Minio.extractUserUUID(tokenFromHeader);
      List<Map<String,String>> fileObjects = fileUploads.stream().map(file -> {
        UUID fileUUID = Minio.saveOnStore(file,userUUID);
        Map<String,String> map = new HashMap<>();
        List<Map<String,String>> list = new ArrayList<>();
        map.put("name", file.fileName());
        if (fileUUID != null) {
          map.put("uuid", fileUUID.toString() );
          System.out.println("File uploaded, name:" + file.fileName() + ", uuid:" + fileUUID.toString());
        } else {
          map.put("uuid", "");
          System.out.println("File NOT uploaded, name:" + file.fileName() + ", uuid set to empty string");
        }
        list.add(map);
        return list;
      }).reduce((acc,element)->{
        acc.addAll(element);
        return acc;
      }).get();
      Map<String,List<Map<String,String>>> map = new HashMap<>();
      map.put("files", fileObjects);
      String json = JsonUtils.toJson(map);
      ctx.response().putHeader("Context-Type", "application/json").end(json);
    }
  }

  public static void userFindFileHandler(RoutingContext ctx) {
    List<String> roles = TokenIntrospection.setRoles("user");
    String tokenFromHeader = Minio.getTokenFromHeader(ctx);
    Boolean isAllowed = TokenIntrospection.checkAuthForRoles(MonoVertx.getInstance().getVertx(), roles, tokenFromHeader);
    if(!isAllowed){
      ctx.response().setStatusCode(401).end();
    }
    else {
      UUID fileUUID = UUID.fromString(ctx.request().getParam("fileuuid"));
      UUID userUUID = Minio.extractUserUUID(tokenFromHeader);
      byte[] fetchFromStore = Minio.fetchFromStoreUserDirectory(fileUUID,userUUID);
      if(fetchFromStore.length == 0) {
        ctx.response().setStatusCode(404).end();
      }else {
        Buffer buffer = Buffer.buffer();
        for(byte e: fetchFromStore) {
          buffer.appendByte(e);
        }
        ctx.response().putHeader("Content-Type", "image/png").end(buffer);
      }
    }
  }

  public static void publicFileUploadHandler(RoutingContext ctx) {
      Set<FileUpload> fileUploads = ctx.fileUploads();
    System.out.println("posted "+fileUploads.size() + " file");
    List<String> roles = TokenIntrospection.setRoles("user");
    String tokenFromHeader = Minio.getTokenFromHeader(ctx);
    String realm = Minio.extractRealm(tokenFromHeader);
    System.out.println("DEBUG: get realm:" + realm + " from token");
    System.out.print("DEBUG: get token from header:" + tokenFromHeader);
    Boolean isAllowed = TokenIntrospection.checkAuthForRoles(MonoVertx.getInstance().getVertx(), roles, tokenFromHeader);
    if(!isAllowed){
      System.out.println("User not allowed to upload file, reject");
      ctx.response().setStatusCode(401).end();
    } else {
      System.out.println("User allowed to upload file.");
      List<Map<String,String>> fileObjects = fileUploads.stream().map(file -> {
        UUID fileUUID = Minio.saveOnStore(file);
        Map<String,String> map = new HashMap<>();
        List<Map<String,String>> list = new ArrayList<>();

        map.put("name", file.fileName());
        if(fileUUID != null) {
          map.put("uuid", fileUUID.toString() );
          System.out.println("File uploaded, name:" + file.fileName() + ", uuid:" + fileUUID.toString());
        } else {
          map.put("uuid", "");
          System.out.println("File NOT uploaded, name:" + file.fileName() + ", uuid set to empty string");
        }

        list.add(map);
        return list;
      }).reduce((acc,element)->{
        acc.addAll(element);
        return acc;
      }).get();
      Map<String,List<Map<String,String>>> map = new HashMap<>();
      map.put("files", fileObjects);
      String json = JsonUtils.toJson(map);
      ctx.response().putHeader("Context-Type", "application/json").end(json);
    }
  }

  public static void fileSize(RoutingContext ctx) {
      UUID fileUUID = UUID.fromString(ctx.request().getParam("fileuuid"));
      ObjectStat stat = Minio.fetchStatFromStorePublicDirectory(fileUUID);
      if (stat != null) {
        long fileSize = stat.length();
        log.debug("fileSize: " + fileSize);

        ctx.response()
                .putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileSize))
                .putHeader(HttpHeaders.ACCEPT_RANGES, "bytes")
                .end(buffer);
      }else{
        ctx.response().setStatusCode(404).end();
      }
  }

  public static void publicFindFileNameHandler(RoutingContext ctx) {
    UUID fileUUID = UUID.fromString(ctx.request().getParam("fileuuid"));
    String fileName = Minio.fetchInfoFromStorePublicDirectory(fileUUID);
    if(fileName.equals("")) {
      ctx.response().setStatusCode(404).end();
    }else {
      ctx.response().putHeader("Content-Type", "application/json")
                    .end(new JsonObject().put("data",new JsonObject().put("name", fileName)).toString());
    }
  }


  public static void publicFindVideoHandler(RoutingContext ctx) {
    UUID fileUUID = UUID.fromString(ctx.request().getParam("fileuuid"));
    ObjectStat stat = Minio.fetchStatFromStorePublicDirectory(fileUUID);
    if(stat.length() == 0) {
      ctx.response().setStatusCode(404).end();
    }else {
      Buffer buffer = Buffer.buffer();
      long videoSize = stat.length();
      String range = ctx.request().getHeader("Range");
      long start = Long.valueOf(range.substring(6).replace("-",""));
      long length = start + CHUNK_SIZE < videoSize ? CHUNK_SIZE : Math.abs(videoSize - start + CHUNK_SIZE - 1 );
      long end = Math.min(start + CHUNK_SIZE, videoSize -1);
      byte[] fetchFromStore = Minio.streamFromStorePublicDirectory(fileUUID,start,length);
      for (byte e : fetchFromStore)
        buffer.appendByte(e);
      ctx.response()
        .setStatusCode(206)
        .putHeader("Content-Range", "bytes " + start + "-" + end + "/" + videoSize)
        .putHeader("Content-Type", "video/mp4")
        .putHeader("Accept-Ranges", "bytes")
        .end(buffer);
    }
  }

  public static void publicFindFileHandler(RoutingContext ctx) {
    UUID fileUUID = UUID.fromString(ctx.request().getParam("fileuuid"));

    byte[] fetchFromStore = Minio.fetchFromStorePublicDirectory(fileUUID);
    String fileName = Minio.fetchInfoFromStorePublicDirectory(fileUUID);
    if(fileName.equals("")) {
      fileName = fileUUID.toString();
    }
    if(fetchFromStore.length == 0) {
      ctx.response().setStatusCode(404).end();

    }else {

      File f = new File("/tmp/"+ fileName);
      String mimeType = null;
      Tika tika = new Tika();
      Buffer buffer = Buffer.buffer();
      try {
        FileUtils.writeByteArrayToFile(f, fetchFromStore);
        mimeType = tika.detect(f);

        if(APPLICATION_X_MATROSKA.equals(mimeType))
          mimeType = "video/webm";
        
        for(byte e: FileUtils.readFileToByteArray(f)) {
          buffer.appendByte(e);
        }

      } catch (IOException e1) {
        // TODO Auto-generated catch block
        e1.printStackTrace();
      }

      f.delete();
      ctx.response().putHeader("Content-Type", mimeType)
                    .putHeader("Content-Disposition",
                        "attachment; filename= ".concat(fileName))
                    .end(buffer);
    }
  }

  public static void publicDeleteFileHandler(RoutingContext ctx) {
    UUID fileUUID = UUID.fromString(ctx.request().getParam("fileuuid"));
    Minio.deleteFromStorePublicDirectory(fileUUID);
    ctx.response().end();
  }

}
