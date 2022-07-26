package life.genny;

import io.minio.ObjectStat;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.core.buffer.Buffer;
import io.vertx.rxjava.ext.web.FileUpload;
import io.vertx.rxjava.ext.web.Router;
import io.vertx.rxjava.ext.web.RoutingContext;
import io.vertx.rxjava.ext.web.handler.BodyHandler;
import io.vertx.rxjava.ext.web.handler.CorsHandler;
import life.genny.qwandautils.JsonUtils;
import life.genny.security.TokenIntrospection;
import life.genny.utils.BufferUtils;
import life.genny.utils.StringUtils;
import life.genny.utils.TemporaryFileStore;
import life.genny.utils.VideoUtils;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpStatus;
import org.apache.tika.Tika;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

public class Server {

    private final static int serverPort;

    private final static String APPLICATION_X_MATROSKA = "application/x-matroska";
    private final static int CHUNK_SIZE = 1000000;

    static {
        serverPort = Optional.ofNullable(System.getenv("MEDIA_PROXY_SERVER_PORT")).map(Integer::valueOf).orElse(8080);
    }

    private static final String X_PINGARUNER = "X-PINGARUNER";
    private static final String X_REQUESTED_WITH = "X-Requested-With";
    private static final String X_TOTAL_COUNT = "X-Total-Count";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String CONNECTION = "Connection";
    private static final String ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
    private static final String CONTENT_RANGE = "Content-Range";

    public static CorsHandler corsHandler() {
        return CorsHandler.create("*").allowedHeader("*").allowedMethod(HttpMethod.GET).allowedMethod(HttpMethod.POST).allowedMethod(HttpMethod.DELETE).allowedMethod(HttpMethod.OPTIONS).allowedHeader(X_PINGARUNER).allowedHeader(CONTENT_TYPE).allowedHeader(X_REQUESTED_WITH).allowedHeader(ACCESS_CONTROL_ALLOW_ORIGIN).allowedHeader(X_TOTAL_COUNT).allowedHeader(CONNECTION).exposedHeader(CONTENT_RANGE);
    }

    public static void run() {
        Vertx vertx = Vertx.newInstance(MonoVertx.getInstance().getVertx());
        Router router = Router.router(vertx);
        router.route().handler(corsHandler());
        /*
         * setBodyLimit expected a long that defines the number of bytes so a file of 100 kilobytes should
         * be written as 100000L
         */
        router.route().handler(BodyHandler.create().setDeleteUploadedFilesOnEnd(true));

        router.route(HttpMethod.POST, "/media").blockingHandler(Server::userFileUploadHandler);

        router.route(HttpMethod.GET, "/media/:fileuuid").blockingHandler(Server::userFindFileHandler);

        router.route(HttpMethod.POST, "/public").blockingHandler(Server::publicFileUploadHandler);

        router.route(HttpMethod.GET, "/public/:fileuuid").blockingHandler(Server::publicFindFileHandler);

        router.route(HttpMethod.GET, "/public/:fileuuid/name").blockingHandler(Server::publicFindFileNameHandler);

        router.route(HttpMethod.GET, "/public/video/:fileuuid").blockingHandler(Server::publicFindVideoHandler);

        router.route(HttpMethod.GET, "/public/video/:videoType/:fileuuid").blockingHandler(Server::publicFindVideoByTypeHandler);

        router.route(HttpMethod.DELETE, "/public/:fileuuid").blockingHandler(Server::publicDeleteFileHandler);
        vertx.createHttpServer().requestHandler(router::accept).listen(serverPort);
    }


    public static void userFileUploadHandler(RoutingContext ctx) {
        try {
            List<String> roles = TokenIntrospection.setRoles("user");
            String tokenFromHeader = Minio.getTokenFromHeader(ctx);
            Boolean isAllowed = TokenIntrospection.checkAuthForRoles(MonoVertx.getInstance().getVertx(), roles, tokenFromHeader);
            if (!isAllowed) {
                ctx.response().setStatusCode(401).end();
            } else {
                Set<FileUpload> fileUploads = ctx.fileUploads();
                UUID userUUID = Minio.extractUserUUID(tokenFromHeader);
                List<Map<String, String>> fileObjects = fileUploads.stream().map(file -> {
                    UUID fileUUID = Minio.saveOnStore(file, userUUID);
                    Map<String, String> map = new HashMap<>();
                    List<Map<String, String>> list = new ArrayList<>();
                    map.put("name", file.fileName());
                    if (fileUUID != null) {
                        map.put("uuid", fileUUID.toString());
                        System.out.println("File uploaded, name:" + file.fileName() + ", uuid:" + fileUUID.toString());
                    } else {
                        map.put("uuid", "");
                        System.out.println("File NOT uploaded, name:" + file.fileName() + ", uuid set to empty string");
                    }
                    list.add(map);
                    return list;
                }).reduce((acc, element) -> {
                    acc.addAll(element);
                    return acc;
                }).get();
                Map<String, List<Map<String, String>>> map = new HashMap<>();
                map.put("files", fileObjects);
                String json = JsonUtils.toJson(map);
                ctx.response().putHeader("Context-Type", "application/json").end(json);
            }
        } catch (Exception ex) {
            System.out.println("Exception : " + ex.getMessage());
        }
    }

    public static void userFindFileHandler(RoutingContext ctx) {
        try {
            List<String> roles = TokenIntrospection.setRoles("user");
            String tokenFromHeader = Minio.getTokenFromHeader(ctx);
            Boolean isAllowed = TokenIntrospection.checkAuthForRoles(MonoVertx.getInstance().getVertx(), roles, tokenFromHeader);
            if (!isAllowed) {
                ctx.response().setStatusCode(401).end();
            } else {
                UUID fileUUID = UUID.fromString(ctx.request().getParam("fileuuid"));
                UUID userUUID = Minio.extractUserUUID(tokenFromHeader);
                byte[] fetchFromStore = Minio.fetchFromStoreUserDirectory(fileUUID, userUUID);
                if (fetchFromStore.length == 0) {
                    ctx.response().setStatusCode(404).end();
                } else {
                    Buffer buffer = Buffer.buffer();
                    for (byte e : fetchFromStore) {
                        buffer.appendByte(e);
                    }
                    ctx.response().putHeader("Content-Type", "image/png").end(buffer);
                }
            }
        } catch (Exception ex) {
            System.out.println("Exception : " + ex.getMessage());
        }
    }

    public static void publicFileUploadHandler(RoutingContext ctx) {
        try {
            Set<FileUpload> fileUploads = ctx.fileUploads();
            System.out.println("posted " + fileUploads.size() + " file");
            List<String> roles = TokenIntrospection.setRoles("user");
            String tokenFromHeader = Minio.getTokenFromHeader(ctx);
            String realm = Minio.extractRealm(tokenFromHeader);
            System.out.println("DEBUG: get realm:" + realm + " from token");
            System.out.print("DEBUG: get token from header:" + tokenFromHeader);
            Boolean isAllowed = TokenIntrospection.checkAuthForRoles(MonoVertx.getInstance().getVertx(), roles, tokenFromHeader);
//            Boolean isAllowed = true;
            if (!isAllowed) {
                System.out.println("User not allowed to upload file, reject");
                ctx.response().setStatusCode(401).end();
            } else {
                System.out.println("User allowed to upload file.");
                List<Map<String, String>> fileObjects = fileUploads.stream().map(file -> {
                    UUID fileUUID = Minio.saveOnStore(file);
                    Map<String, String> map = new HashMap<>();
                    List<Map<String, String>> list = new ArrayList<>();

                    map.put("name", file.fileName());
                    if (fileUUID != null) {
                        map.put("uuid", fileUUID.toString());
                        System.out.println("File uploaded, name:" + file.fileName() + ", uuid:" + fileUUID.toString());
                    } else {
                        map.put("uuid", "");
                        System.out.println("File NOT uploaded, name:" + file.fileName() + ", uuid set to empty string");
                    }

                    list.add(map);
                    return list;
                }).reduce((acc, element) -> {
                    acc.addAll(element);
                    return acc;
                }).get();
                Map<String, List<Map<String, String>>> map = new HashMap<>();
                map.put("files", fileObjects);
                String json = JsonUtils.toJson(map);
                ctx.response().putHeader("Context-Type", "application/json").end(json);
            }
        } catch (Exception ex) {
            System.out.println("Exception : " + ex.getMessage());
        }
    }

    public static void publicFindFileNameHandler(RoutingContext ctx) {
        UUID fileUUID = UUID.fromString(ctx.request().getParam("fileuuid"));
        String fileName = Minio.fetchInfoFromStorePublicDirectory(fileUUID);
        if (fileName.equals("")) {
            ctx.response().setStatusCode(404).end();
        } else {
            ctx.response().putHeader("Content-Type", "application/json").end(new JsonObject().put("data", new JsonObject().put("name", fileName)).toString());
        }
    }




    public static void publicFindVideoHandler(RoutingContext ctx) {
        UUID fileUUID = UUID.fromString(ctx.request().getParam("fileuuid"));
        ObjectStat stat = Minio.fetchStatFromStorePublicDirectory(fileUUID);
        System.out.println(stat);
        if (stat.length() == 0) {
            ctx.response().setStatusCode(404).end();
        } else {
            long start = 0;
            long end = 0;
            long rangeLength = 0;
            long videoSize = stat.length();
            System.out.println("#### Video Size: "+ videoSize);
            String range = ctx.request().getHeader("Range");

            if(range != null){
                System.out.println("#### Range: "+ range);

                String[] ranges = range.substring(6).split("-");

                if(ranges.length == 1){
                    start = Long.valueOf(ranges[0]);
                    System.out.println("#### Range - Start: "+ end);
                }

                if(ranges.length == 2){
                    end = Long.valueOf(ranges[1]);
                    System.out.println("#### Range - End: "+ end);
                }else{
                    end = videoSize-1;
                }

                 rangeLength = Math.min(1024 * 1024L, end - start + 1);
                System.out.println("#### rangeLength: "+ rangeLength);
                System.out.println("#### ---------- ####");
            }else{
                rangeLength = Math.min(1024 * 1024L, videoSize);
                System.out.println("#### rangeLength: "+ rangeLength);
            }


            byte[] fetchFromStore = Minio.streamFromStorePublicDirectory(fileUUID, start, rangeLength);

            Tika tika = new Tika();
            String mimeType = tika.detect(fetchFromStore);
            if (APPLICATION_X_MATROSKA.equals(mimeType)) mimeType = "video/webm";

            Buffer buffer = Buffer.buffer(fetchFromStore);

            ctx.response()
                    .setStatusCode(HttpStatus.SC_PARTIAL_CONTENT)
                    .putHeader(HttpHeaders.KEEP_ALIVE, "timeout=5, max=99")
                    .putHeader(HttpHeaders.CONNECTION, "Keep-Alive")
                    .putHeader(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + videoSize)
                    .putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(fetchFromStore.length))
                    .putHeader(HttpHeaders.CONTENT_TYPE, mimeType)
                    .putHeader(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .end(buffer);
        }
    }

    public static void publicFindFileHandler(RoutingContext ctx) {
        UUID fileUUID = UUID.fromString(ctx.request().getParam("fileuuid"));

        byte[] fetchFromStore = Minio.fetchFromStorePublicDirectory(fileUUID);
        String fileName = Minio.fetchInfoFromStorePublicDirectory(fileUUID);
        if (fileName.equals("")) {
            fileName = fileUUID.toString();
        }
        if (fetchFromStore.length == 0) {
            ctx.response().setStatusCode(404).end();

        } else {

            File f = new File("/tmp/" + fileName);
            System.out.println("filename: " + fileName);
            String mimeType = null;
            Tika tika = new Tika();
            Buffer buffer = Buffer.buffer();
            try {
                FileUtils.writeByteArrayToFile(f, fetchFromStore);

                mimeType = tika.detect(f);
                System.out.println("mimeType:" + mimeType);

                for (byte e : FileUtils.readFileToByteArray(f)) {
                    buffer.appendByte(e);
                }

            } catch (IOException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }

            f.delete();
            if (mimeType.startsWith("video/") || APPLICATION_X_MATROSKA.equals(mimeType)) {
                String uuid = UUID.randomUUID().toString();
                System.out.println("##### vDetected Video Type");
                if (fileName.split("\\.").length == 1) {
                    if (APPLICATION_X_MATROSKA.equals(mimeType)) {
                        System.out.println("##### APPLICATION_X_MATROSKA Detected");
                        mimeType = "video/webm";
                        fileName = uuid + ".webm";
                    } else {
                        System.out.println("Cannot detect extension at run time enforcing MP4");
                        mimeType = "video/mp4";
                        fileName = uuid + ".mp4";
                    }
                } else {
                    System.out.println("#### Extension Found");
                    String[] splitted = fileName.split("\\.");
                    String extension = splitted[splitted.length - 1];
                    fileName = uuid + "." + extension;
                }
                System.out.println("##### Downloaded fileName: " + fileName);
            }


            ctx.response().putHeader("Content-Type", mimeType).putHeader("Content-Disposition", "attachment; filename= ".concat(fileName)).end(buffer);

        }
    }

    public static void publicFindVideoByTypeHandler(RoutingContext ctx) {
        try {
            String fileUuid = ctx.request().getParam("fileUuid");
            System.out.println("#### Request uuid: " + fileUuid);
            UUID fileUUID = UUID.fromString(fileUuid);
            String videoType = ctx.request().getParam("videoType");

            byte[] fetchFromStore = Minio.fetchFromStorePublicDirectory(fileUUID);
            String fileName = Minio.fetchInfoFromStorePublicDirectory(fileUUID);

            Buffer outputBuffer = null;
            String mimeType = null;

            if (fileName.equals("")) {
                fileName = fileUUID.toString();
            }

            if (fetchFromStore.length == 0) {
                ctx.response().setStatusCode(404).end();
            } else {
                File input = TemporaryFileStore.createTemporaryFile(fileName);
                FileUtils.writeByteArrayToFile(input, fetchFromStore);
                if (fileName.contains(".mp4")) {
                    System.out.println("#### Mp4 detected");
                    outputBuffer = BufferUtils.fileToBuffer(input);
                    System.out.println("#### Extension Found");
                    fileName = StringUtils.fileNameToUuid(fileName);
                } else {
                    System.out.println("#### Non Mp4 detected");
                    Tika tika = new Tika();
                    mimeType = tika.detect(input);
                    System.out.println("#### MinIO file mimeType: " + mimeType);
                    if (mimeType.startsWith("video/") || APPLICATION_X_MATROSKA.equals(mimeType)) {
                        File target = VideoUtils.convert(input, videoType);
                        mimeType = tika.detect(target);
                        System.out.println("#### Converted file mimeType: " + mimeType);
                        outputBuffer = BufferUtils.fileToBuffer(target);
                        String newFileName = UUID.randomUUID().toString();
                        fileName = newFileName + "." + videoType;
                        target.delete();
                    }
                }
                System.out.println("#### fileName: " + fileName);
                input.delete();
                ctx.response().putHeader("Content-Type", mimeType).putHeader("Access-Control-Expose-Headers", "Content-Disposition").putHeader("Content-Disposition", "attachment; filename= ".concat(fileName)).end(outputBuffer);
            }

        } catch (Exception ex) {
            System.out.println("Exception: " + ex.getMessage());
        }
    }

    public static void publicDeleteFileHandler(RoutingContext ctx) {
        UUID fileUUID = UUID.fromString(ctx.request().getParam("fileuuid"));
        Minio.deleteFromStorePublicDirectory(fileUUID);
        ctx.response().end();
    }

}
