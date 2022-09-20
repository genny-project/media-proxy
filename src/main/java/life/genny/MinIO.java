package life.genny;

import com.amazonaws.util.IOUtils;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.UploadObjectArgs;
import io.vertx.rxjava.core.MultiMap;
import io.vertx.rxjava.ext.web.FileUpload;
import io.vertx.rxjava.ext.web.RoutingContext;
import life.genny.qwandautils.KeycloakUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.util.Optional;
import java.util.UUID;

public class MinIO {
    private static final Logger log = LoggerFactory.getLogger(MinIO.class);
    private static MinioClient minioClient;
    private static String REALM = Optional.ofNullable(System.getenv("REALM")).orElse("internmatch");

    static {
        try {
            minioClient = MinioClient
                    .builder()
                    .endpoint(EnvironmentVariables.MINIO_SERVER_URL)
                    .credentials(EnvironmentVariables.MINIO_ACCESS_KEY, EnvironmentVariables.MINIO_PRIVATE_KEY)
                    .build();
        } catch (Exception ex) {
            log.error("Exception: " + ex.getMessage());
        }
    }

    public static String getTokenFromHeader(RoutingContext ctx) {
        MultiMap headers = ctx.request().headers();
        String authValue = headers.get("Authorization");
        String[] split = authValue.split(" ");
        String token = split[1];
        return token;
    }

    public static String extractRealm(String token) throws Exception {
        JSONObject decodedToken = KeycloakUtils.getDecodedToken(token);
        String kcRealmUrl = decodedToken.get("iss").toString();
        String[] split = kcRealmUrl.split("/");
        int length = split.length;
        String realm = split[length - 1];
        return realm;
    }

    public static UUID extractUserUUID(String token) throws Exception {
        JSONObject decodedToken = KeycloakUtils.getDecodedToken(token);
        UUID userUUID = UUID.fromString(decodedToken.get("sub").toString());
        return userUUID;
    }

    public static UUID saveOnStore(FileUpload file) {
        UUID randomUUID = UUID.randomUUID();
        String fileInfoName = "file-uploads/".concat(randomUUID.toString().concat("-info"));
        File fileInfo = new File(fileInfoName);
        try (FileWriter myWriter = new FileWriter(fileInfo.getPath());) {
            myWriter.write(file.fileName());
        } catch (Exception ex) {
            log.error("Exception: " + ex.getMessage());
        }
        Boolean isFileUploaded = uploadFile(REALM.concat("/") + "public", file.uploadedFileName(), randomUUID.toString());
        Boolean isFileInfoUploaded = uploadFile(REALM.concat("/") + "public", fileInfo.getPath(), randomUUID.toString().concat("-info"));
        if (isFileUploaded && isFileInfoUploaded) {
            fileInfo.delete();
            return randomUUID;
        } else {
            return null;
        }
    }

    public static String saveOnStore(String fileName, File file) {
        Boolean isFileUploaded = uploadFile(REALM.concat("/") + "public", file.getPath(), fileName);
        if (isFileUploaded) {
            return fileName;
        } else {
            return null;
        }
    }

    public static UUID saveOnStore(FileUpload file, UUID userUUID) {
        UUID randomUUID = UUID.randomUUID();
        Boolean isFileUploaded = uploadFile(userUUID.toString(), file.uploadedFileName(), randomUUID.toString());
        if (isFileUploaded) {
            return randomUUID;
        } else {
            return null;
        }
    }

    public static byte[] fetchFromStoreUserDirectory(UUID fileUUID, UUID userUUID) {
        try {
            String fullPath = REALM + "/" + userUUID.toString() + "/" + "media" + "/" + fileUUID.toString().concat("-info");
            GetObjectArgs getObjectArgs = GetObjectArgs
                    .builder()
                    .bucket(EnvironmentVariables.BUCKET_NAME)
                    .object(fullPath)
                    .build();
            GetObjectResponse getObjectResponse = minioClient.getObject(getObjectArgs);
            byte[] byteArray = IOUtils.toByteArray(getObjectResponse);
            return byteArray;
        } catch (Exception ex) {
            log.error("Exception: " + ex.getMessage());
            return new byte[]{};
        }
    }

    public static StatObjectResponse fetchStatFromStorePublicDirectory(UUID fileUUID) {
        return fetchStatFromStorePublicDirectory(fileUUID.toString());
    }

    public static StatObjectResponse fetchStatFromStorePublicDirectory(String fileUUID) {
        try {
            String fullPath = REALM + "/" + "public" + "/" + "media" + "/" + fileUUID.toString();
            StatObjectArgs statObjectArgs = StatObjectArgs
                    .builder()
                    .bucket(EnvironmentVariables.BUCKET_NAME)
                    .object(fullPath)
                    .build();
            StatObjectResponse statObjectResponse = minioClient.statObject(statObjectArgs);
            return statObjectResponse;
        } catch (Exception ex) {
            log.error("Exception: " + ex.getMessage());
            return null;
        }
    }

    public static String fetchInfoFromStorePublicDirectory(UUID fileUUID) {
        try {
            String fullPath = REALM + "/" + "public" + "/" + "media" + "/" + fileUUID.toString().concat("-info");
            GetObjectArgs getObjectArgs = GetObjectArgs
                    .builder()
                    .bucket(EnvironmentVariables.BUCKET_NAME)
                    .object(fullPath)
                    .build();
            GetObjectResponse getObjectResponse = minioClient.getObject(getObjectArgs);
            byte[] byteArray = IOUtils.toByteArray(getObjectResponse);
            return new String(byteArray);
        } catch (Exception ex) {
            log.error("Exception: " + ex.getMessage());
            return "";
        }
    }

    public static byte[] streamFromStorePublicDirectory(UUID fileUUID, Long start, Long end) {
        return streamFromStorePublicDirectory(fileUUID.toString(), start, end);
    }

    public static byte[] streamFromStorePublicDirectory(String fileUUID, Long start, Long end) {
        try {
            String fullPath = REALM + "/" + "public" + "/" + "media" + "/" + fileUUID.toString();
            GetObjectArgs getObjectArgs = GetObjectArgs
                    .builder()
                    .bucket(EnvironmentVariables.BUCKET_NAME)
                    .object(fullPath)
                    .offset(start)
                    .length(end)
                    .build();
            GetObjectResponse getObjectResponse = minioClient.getObject(getObjectArgs);
            byte[] byteArray = IOUtils.toByteArray(getObjectResponse);
            return byteArray;
        } catch (Exception ex) {
            log.error("Exception: " + ex.getMessage());
            return new byte[]{};
        }
    }

    public static byte[] fetchFromStorePublicDirectory(UUID fileUUID) {
        try {
            String fullPath = REALM + "/" + "public" + "/" + "media" + "/" + fileUUID.toString();
            GetObjectArgs getObjectArgs = GetObjectArgs
                    .builder()
                    .bucket(EnvironmentVariables.BUCKET_NAME)
                    .object(fullPath)
                    .build();
            GetObjectResponse getObjectResponse = minioClient.getObject(getObjectArgs);
            byte[] byteArray = IOUtils.toByteArray(getObjectResponse);
            return byteArray;
        } catch (Exception ex) {
            log.error("Exception: " + ex.getMessage());
            return new byte[]{};
        }
    }

    public static byte[] fetchFromStorePublicDirectory(String fileName) {
        try {
            String fullPath = REALM + "/" + "public" + "/" + "media" + "/" + fileName;
            GetObjectArgs getObjectArgs = GetObjectArgs
                    .builder()
                    .bucket(EnvironmentVariables.BUCKET_NAME)
                    .object(fullPath)
                    .build();
            GetObjectResponse getObjectResponse = minioClient.getObject(getObjectArgs);
            byte[] byteArray = IOUtils.toByteArray(getObjectResponse);
            return byteArray;
        } catch (Exception ex) {
            log.error("Exception: " + ex.getMessage());
            return new byte[]{};
        }
    }

    public static void deleteFromStorePublicDirectory(UUID fileUUID) {
        try {
            String fullPath = REALM + "/" + "public" + "/" + "media" + "/" + fileUUID.toString();
            RemoveObjectArgs removeObjectArgs = RemoveObjectArgs
                    .builder()
                    .bucket(EnvironmentVariables.BUCKET_NAME)
                    .object(fullPath)
                    .build();
            minioClient.removeObject(removeObjectArgs);
        } catch (Exception ex) {
            log.error("Exception: " + ex.getMessage());
        }
    }

    public static boolean uploadFile(String sub, String inpt, String uuid) {
        boolean isSuccess = false;

        String path = sub + "/" + "media" + "/" + uuid;
        try {
            BucketExistsArgs bucketExistsArgs = BucketExistsArgs
                    .builder()
                    .bucket(EnvironmentVariables.BUCKET_NAME)
                    .build();

            boolean isExist = minioClient.bucketExists(bucketExistsArgs);
            if (isExist) {
                log.debug("Bucket " + EnvironmentVariables.BUCKET_NAME + "already exists.");
            } else {
                log.debug("Start creat Bucket:" + EnvironmentVariables.BUCKET_NAME);
                MakeBucketArgs makeBucketArgs = MakeBucketArgs.builder()
                        .bucket(EnvironmentVariables.BUCKET_NAME)
                        .build();
                minioClient.makeBucket(makeBucketArgs);
                log.debug("Finish create Bucket:" + EnvironmentVariables.BUCKET_NAME);
            }

            UploadObjectArgs uploadObjectArgs = UploadObjectArgs
                    .builder()
                    .bucket(EnvironmentVariables.BUCKET_NAME)
                    .object(path)
                    .filename(inpt)
                    .build();

            minioClient.uploadObject(uploadObjectArgs);

            isSuccess = true;
            log.debug("Success, File" + inpt + " uploaded to bucket with path:" + path);
        } catch (Exception ex) {
            log.error("Exception: " + ex.getMessage());
        }
        return isSuccess;
    }
}
