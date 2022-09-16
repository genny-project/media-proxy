package life.genny;

import io.minio.StatObjectResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.core.buffer.Buffer;
import io.vertx.rxjava.ext.web.FileUpload;
import io.vertx.rxjava.ext.web.Router;
import io.vertx.rxjava.ext.web.RoutingContext;
import io.vertx.rxjava.ext.web.handler.BodyHandler;
import io.vertx.rxjava.ext.web.handler.CorsHandler;
import life.genny.constants.VideoConstants;
import life.genny.qwandautils.JsonUtils;
import life.genny.security.TokenIntrospection;
import life.genny.utils.BufferUtils;
import life.genny.utils.ConvertUtils;
import life.genny.utils.QualityUtils;
import life.genny.utils.TemporaryFileStore;
import life.genny.utils.VideoUtils;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpStatus;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class Server {
    private static final Logger log = LoggerFactory.getLogger(Server.class);
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
        return CorsHandler
                .create("*")
                .allowedHeader("*")
                .allowedMethod(HttpMethod.GET)
                .allowedMethod(HttpMethod.POST)
                .allowedMethod(HttpMethod.DELETE)
                .allowedMethod(HttpMethod.OPTIONS)
                .allowedHeader(X_PINGARUNER)
                .allowedHeader(CONTENT_TYPE)
                .allowedHeader(X_REQUESTED_WITH)
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
        router
                .route()
                .handler(BodyHandler.create().setDeleteUploadedFilesOnEnd(true));

        router
                .route(HttpMethod.POST, "/media")
                .blockingHandler(Server::userFileUploadHandler, false);

        router
                .route(HttpMethod.GET, "/media/:fileuuid")
                .blockingHandler(Server::userFindFileHandler, false);

        router
                .route(HttpMethod.POST, "/public")
                .blockingHandler(Server::publicFileUploadHandler, false);

        router
                .route(HttpMethod.GET, "/public/:fileuuid")
                .blockingHandler(Server::publicFindFileHandler, false);

        router
                .route(HttpMethod.GET, "/public/:fileuuid/name")
                .blockingHandler(Server::publicFindFileNameHandler, false);

        router
                .route(HttpMethod.GET, "/public/video/:fileuuid")
                .blockingHandler(Server::publicFindVideoHandler, false);

        router
                .route(HttpMethod.GET, "/public/video/mp4/:quality/:fileuuid")
                .blockingHandler(Server::publicFindVideoByTypeHandler, false);

        router
                .route(HttpMethod.HEAD, "/public/video/:fileuuid")
                .blockingHandler(Server::getVideoSize, false);

        router
                .route(HttpMethod.DELETE, "/public/:fileuuid")
                .blockingHandler(Server::publicDeleteFileHandler, false);

        router
                .route(HttpMethod.GET, "/public/convert/:fileuuid")
                .blockingHandler(Server::publicConvertToMultipleQualities, false);

        vertx
                .createHttpServer()
                .requestHandler(router::accept)
                .listen(serverPort);
    }

    public static void getVideoSize(RoutingContext ctx) {
        UUID fileUUID = UUID.fromString(ctx.request().getParam("fileuuid"));
        StatObjectResponse stat = Minio.fetchStatFromStorePublicDirectory(fileUUID);
        if (stat.size() == 0) {
            ctx.response().setStatusCode(404).end();
        } else {
            long videoSize = stat.size();
            log.debug("#### videoSize: " + videoSize);
            ctx.response()
                    .putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(videoSize))
                    .putHeader(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .end();
        }
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
                        log.debug("File uploaded, name:" + file.fileName() + ", uuid:" + fileUUID.toString());
                    } else {
                        map.put("uuid", "");
                        log.debug("File NOT uploaded, name:" + file.fileName() + ", uuid set to empty string");
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
            log.error("Exception : " + ex.getMessage());
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
            log.error("Exception : " + ex.getMessage());
        }
    }

    public static void publicFileUploadHandler(RoutingContext ctx) {
        try {
            Set<FileUpload> fileUploads = ctx.fileUploads();
            log.debug("posted " + fileUploads.size() + " file");
            List<String> roles = TokenIntrospection.setRoles("user");
            String tokenFromHeader = Minio.getTokenFromHeader(ctx);
            String realm = Minio.extractRealm(tokenFromHeader);
            log.debug("DEBUG: get realm:" + realm + " from token");
            System.out.print("DEBUG: get token from header:" + tokenFromHeader);
            Boolean isAllowed = TokenIntrospection.checkAuthForRoles(MonoVertx.getInstance().getVertx(), roles, tokenFromHeader);
//            Boolean isAllowed = true;
            if (!isAllowed) {
                log.debug("User not allowed to upload file, reject");
                ctx.response().setStatusCode(401).end();
            } else {
                log.debug("User allowed to upload file.");
                List<Map<String, String>> fileObjects = fileUploads.stream().map(file -> {
                    File input = new File(file.uploadedFileName());
                    Tika tika = new Tika();
                    UUID fileUUID = null;
                    try {
                        String mimeType = tika.detect(input);
                        if (mimeType.startsWith("video/") || APPLICATION_X_MATROSKA.equals(mimeType)) {
                            fileUUID = ConvertUtils.saveWithMultipleQualities(file);
                        } else {
                            fileUUID = Minio.saveOnStore(file);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    Map<String, String> map = new HashMap<>();
                    List<Map<String, String>> list = new ArrayList<>();

                    map.put("name", file.fileName());
                    if (fileUUID != null) {
                        map.put("uuid", fileUUID.toString());
                        log.debug("File uploaded, name:" + file.fileName() + ", uuid:" + fileUUID.toString());
                    } else {
                        map.put("uuid", "");
                        log.debug("File NOT uploaded, name:" + file.fileName() + ", uuid set to empty string");
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
            log.error("Exception : " + ex.getMessage());
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
        log.debug("#### Handler Thread is: " + Thread.currentThread().getName());
        String fileUUID = ctx.request().getParam("fileuuid");
        StatObjectResponse stat = Minio.fetchStatFromStorePublicDirectory(fileUUID + "-mp4-360p");
        if (stat.size() == 0) {
            ctx.response().setStatusCode(404).end();
        } else {
            long videoSize = stat.size();
            log.debug("#### Video Size: " + videoSize);

            String range = ctx.request().getHeader("Range");
            long rangeStart = 0;
            long rangeEnd;

            String[] ranges = range.split("-");
            log.debug("#### ranges: " + Arrays.toString(ranges));
            rangeStart = Long.parseLong(ranges[0].substring(6));

            if (ranges.length > 1) {
                rangeEnd = Long.parseLong(ranges[1]);
            } else {
                rangeEnd = videoSize - 1;
            }

            if (videoSize < rangeEnd) {
                rangeEnd = videoSize - 1;
            }

            log.debug("#### rangeStart: " + rangeStart);
            log.debug("#### rangeEnd: " + rangeEnd);

            String contentLength = String.valueOf(Math.min(1024 * 1024L, rangeEnd - rangeStart + 1));
            log.debug("#### contentLength: " + contentLength);

            byte[] fetchFromStore = Minio.streamFromStorePublicDirectory(fileUUID + VideoConstants.suffix360p, rangeStart, Long.valueOf(contentLength));
            if (fetchFromStore.length == 0) {
                log.debug("#### Video not found");
                ctx.response().setStatusCode(404).end();
            } else {
                ctx.response()
                        .setStatusCode(HttpStatus.SC_PARTIAL_CONTENT)
                        .putHeader(HttpHeaders.CONTENT_RANGE, "bytes " + rangeStart + "-" + rangeEnd + "/" + videoSize)
                        .putHeader(HttpHeaders.CONTENT_LENGTH, contentLength)
                        .putHeader(HttpHeaders.CONTENT_TYPE, "video/mp4")
                        .putHeader(HttpHeaders.ACCEPT_RANGES, "bytes")
                        .end(Buffer.buffer(fetchFromStore));
            }
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
            log.debug("filename: " + fileName);
            String mimeType = null;
            Tika tika = new Tika();
            Buffer buffer = Buffer.buffer();
            try {
                FileUtils.writeByteArrayToFile(f, fetchFromStore);

                mimeType = tika.detect(f);
                log.debug("mimeType:" + mimeType);

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
                log.debug("##### vDetected Video Type");
                if (fileName.split("\\.").length == 1) {
                    if (APPLICATION_X_MATROSKA.equals(mimeType)) {
                        log.debug("##### APPLICATION_X_MATROSKA Detected");
                        mimeType = "video/webm";
                        fileName = uuid + ".webm";
                    } else {
                        log.debug("Cannot detect extension at run time enforcing MP4");
                        mimeType = "video/mp4";
                        fileName = uuid + ".mp4";
                    }
                } else {
                    log.debug("#### Extension Found");
                    String[] splitted = fileName.split("\\.");
                    String extension = splitted[splitted.length - 1];
                    fileName = uuid + "." + extension;
                }
                log.debug("##### Downloaded fileName: " + fileName);
            }


            ctx.response().putHeader("Content-Type", mimeType).putHeader("Content-Disposition", "attachment; filename= ".concat(fileName)).end(buffer);

        }
    }

    public static void publicFindVideoByTypeHandler(RoutingContext ctx) {
        log.debug("#### Handler Thread is: " + Thread.currentThread().getName());

        try {
            String fileUUID = ctx.request().getParam("fileUUID");
            log.debug("#### Request uuid: " + fileUUID);
            String quality = ctx.request().getParam("quality");

            String mp4Video720FileName = fileUUID + VideoConstants.suffix720p;
            String mp4Video360FileName = fileUUID + VideoConstants.suffix360p;

            byte[] fetchFromStore = null;

            if (quality.equals("360")) {
                fetchFromStore = Minio.fetchFromStorePublicDirectory(mp4Video360FileName);
            } else if (quality.equals("720")) {
                fetchFromStore = Minio.fetchFromStorePublicDirectory(mp4Video720FileName);
            } else {
                log.debug("#### Video not found");
                ctx.response().setStatusCode(404).end();
            }

            Buffer outputBuffer = null;

            if (fetchFromStore.length != 0) {
                File input = TemporaryFileStore.createTemporaryFile(fileUUID);
                FileUtils.writeByteArrayToFile(input, fetchFromStore);
                outputBuffer = BufferUtils.fileToBuffer(input);
                input.delete();
            } else {
                ctx.response().setStatusCode(404).end();
            }

            ctx
                    .response()
                    .putHeader("Content-Type", "video/mp4")
                    .putHeader("Access-Control-Expose-Headers", "Content-Disposition")
                    .putHeader("Content-Disposition", "attachment; filename= ".concat(fileUUID).concat(".mp4"))
                    .end(outputBuffer);

        } catch (Exception ex) {
            ctx.response().setStatusCode(404).end();
            log.error("Exception: " + ex.getMessage());
        }
    }

    public static void publicConvertToMultipleQualities(RoutingContext ctx) {
        try {
            String fileUuid = ctx.request().getParam("fileUuid");
            byte[] originalVideoByteArray = Minio.fetchFromStorePublicDirectory(fileUuid);

            if (originalVideoByteArray.length == 0) {
                log.debug("#### Video not found");
                ctx.response().setStatusCode(404).end();
            } else {
                log.debug("#### Video found");
                log.debug("#### Converting started");
                ConvertUtils.convert(fileUuid, originalVideoByteArray);
                log.debug("#### Converting ended");
                ctx.response().setStatusCode(200).end();
            }
        } catch (Exception ex) {
            log.error("Exception: " + ex.getMessage());
            ctx.response().setStatusCode(500).end();
        }
    }

    public static void publicDeleteFileHandler(RoutingContext ctx) {
        UUID fileUUID = UUID.fromString(ctx.request().getParam("fileuuid"));
        Minio.deleteFromStorePublicDirectory(fileUUID);
        ctx.response().end();
    }

}
