package life.genny;

import com.amazonaws.util.IOUtils;
import io.minio.MinioClient;
import io.minio.ObjectStat;
import io.minio.errors.*;
import io.vertx.rxjava.core.MultiMap;
import io.vertx.rxjava.ext.web.FileUpload;
import io.vertx.rxjava.ext.web.RoutingContext;
import life.genny.qwandautils.KeycloakUtils;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.UUID;

public class Minio {
  protected static final Logger log = org.apache.logging.log4j.LogManager.getLogger(Minio.class);
  private static MinioClient minioClient;
  private static String REALM = Optional.ofNullable(System.getenv("REALM")).orElse("internmatch");
  static {
    try {
      minioClient =
          new MinioClient(EnvironmentVariables.MINIO_SERVER_URL,
              EnvironmentVariables.MINIO_ACCESS_KEY,
              EnvironmentVariables.MINIO_PRIVATE_KEY);
    } catch (InvalidEndpointException e) {
      e.printStackTrace();
    } catch (InvalidPortException e) {
      e.printStackTrace();
    }
  }

  public static String getTokenFromHeader(RoutingContext ctx) {
    MultiMap headers = ctx.request().headers();
    String authValue = headers.get("Authorization");
//    log.debug("DEBUG, authValue:" + authValue);
    String[] split = authValue.split(" ");
//    log.debug("DEBUG, split:" + split);
    String token = split[1];
//    log.debug("DEBUG, token:" + token);
    return token;
  }

  public static String extractRealm(String token) throws Exception{
    JSONObject decodedToken = KeycloakUtils.getDecodedToken(token);
    String kcRealmUrl = decodedToken.get("iss").toString();
    String[] split = kcRealmUrl.split("/");
    int length = split.length;
    String realm = split[length-1];
    return realm;
  }
  public static UUID extractUserUUID(String token) throws Exception{
    JSONObject decodedToken = KeycloakUtils.getDecodedToken(token);
    UUID userUUID = UUID.fromString(decodedToken.get("sub").toString());
    return userUUID;
  }

  public static UUID saveOnStore(FileUpload file) {
    UUID randomUUID = UUID.randomUUID();
    String fileInfoName = "file-uploads/".concat(randomUUID.toString().concat("-info"));
    File fileInfo = new File(fileInfoName);
    try(FileWriter myWriter = new FileWriter(fileInfo.getPath());) {
      myWriter.write(file.fileName());
    } catch (IOException e) {
      e.printStackTrace();
    }

    if (uploadFile(REALM.concat("/")+"public",file.uploadedFileName(),randomUUID.toString()) &&
            uploadFile(REALM.concat("/")+"public",fileInfo.getPath(),randomUUID.toString().concat("-info"))) {
      fileInfo.delete();
      return randomUUID;
    } else {
      return null;
    }
  }

  public static UUID saveOnStore(FileUpload file,UUID userUUID) {
    UUID randomUUID = UUID.randomUUID();
    if (uploadFile(userUUID.toString(), file.uploadedFileName(), randomUUID.toString())) {
      return randomUUID;
    } else {
      return null;
    }
  }

  public static byte[] fetchFromStoreUserDirectory(UUID fileUUID, UUID userUUID) {
    try {
      InputStream object = minioClient.getObject(EnvironmentVariables.BUCKET_NAME,
          userUUID.toString() + "/media/" + fileUUID.toString());
      byte[] byteArray = IOUtils.toByteArray(object);
      return byteArray;
    } catch (InvalidKeyException | InvalidBucketNameException
        | NoSuchAlgorithmException | InsufficientDataException
        | NoResponseException | ErrorResponseException
        | InternalException | InvalidArgumentException | IOException
        | XmlPullParserException e) {
      e.printStackTrace();
      return new byte[] {};
    }
  }

  public static ObjectStat fetchStatFromStorePublicDirectory(UUID fileUUID) {
    try {
      ObjectStat object = minioClient.statObject(EnvironmentVariables.BUCKET_NAME,
           REALM + "/" + 
           "public" + "/" + 
           "media"  + "/" + 
           fileUUID.toString());
      return object;
    } catch (InvalidKeyException | InvalidBucketNameException
        | NoSuchAlgorithmException | InsufficientDataException
        | NoResponseException | ErrorResponseException
        | InternalException | IOException
        | XmlPullParserException e) {
      e.printStackTrace();
      return null;
    }
  }
  public static String fetchInfoFromStorePublicDirectory(UUID fileUUID) {
    try {
      InputStream object = minioClient.getObject(EnvironmentVariables.BUCKET_NAME,
           REALM + "/" + 
           "public" + "/" + 
           "media"  + "/" + 
           fileUUID.toString().concat("-info"));
      byte[] byteArray = IOUtils.toByteArray(object);
      return new String(byteArray);
    } catch (InvalidKeyException | InvalidBucketNameException
        | NoSuchAlgorithmException | InsufficientDataException
        | NoResponseException | ErrorResponseException
        | InternalException | InvalidArgumentException | IOException
        | XmlPullParserException e) {
      e.printStackTrace();
      return "";
    }
  }

  public static byte[] streamFromStorePublicDirectory(UUID fileUUID,Long start, Long end) {
    try {
      InputStream object = minioClient.getObject(EnvironmentVariables.BUCKET_NAME,
           REALM + "/" + 
           "public" + "/" + 
           "media"  + "/" + 
           fileUUID.toString(),start,end);
      byte[] byteArray = IOUtils.toByteArray(object);
      return byteArray;
    } catch (InvalidKeyException | InvalidBucketNameException
        | NoSuchAlgorithmException | InsufficientDataException
        | NoResponseException | ErrorResponseException
        | InternalException | InvalidArgumentException | IOException
        | XmlPullParserException e) {
      e.printStackTrace();
      return new byte[] {};
    }
  }
  public static byte[] fetchFromStorePublicDirectory(UUID fileUUID) {
    try {
      InputStream object = minioClient.getObject(EnvironmentVariables.BUCKET_NAME,
           REALM + "/" + 
           "public" + "/" + 
           "media"  + "/" + 
           fileUUID.toString());
      byte[] byteArray = IOUtils.toByteArray(object);
      return byteArray;
    } catch (InvalidKeyException | InvalidBucketNameException
        | NoSuchAlgorithmException | InsufficientDataException
        | NoResponseException | ErrorResponseException
        | InternalException | InvalidArgumentException | IOException
        | XmlPullParserException e) {
      e.printStackTrace();
      return new byte[] {};
    }
  }

  public static void deleteFromStorePublicDirectory(UUID fileUUID) {
    try {
      minioClient.removeObject(EnvironmentVariables.BUCKET_NAME,
           REALM + "/" + 
           "public" + "/" + 
           "media"  + "/" +
           fileUUID.toString());
    } catch (InvalidKeyException | InvalidBucketNameException
        | NoSuchAlgorithmException | InsufficientDataException
        | NoResponseException | ErrorResponseException
        | InternalException | InvalidArgumentException | IOException
        | XmlPullParserException e) {
      e.printStackTrace();
    }
  }

  public static boolean uploadFile(String sub, String inpt, String uuid) {
    boolean isSuccess = false;

    String path = sub + "/" + "media" + "/" + uuid;
    try {
      boolean isExist = minioClient.bucketExists(EnvironmentVariables.BUCKET_NAME);
      if (isExist) {
        log.debug("Bucket " + EnvironmentVariables.BUCKET_NAME +  "already exists.");
      } else {
        log.debug("Start creat Bucket:" + EnvironmentVariables.BUCKET_NAME);
        minioClient.makeBucket(EnvironmentVariables.BUCKET_NAME);
        log.debug("Finish create Bucket:" + EnvironmentVariables.BUCKET_NAME);
      }

      minioClient.putObject(EnvironmentVariables.BUCKET_NAME, path, inpt);
      isSuccess = true;
      log.debug("Success, File" + inpt +  " uploaded to bucket with path:" + path);
    } catch (MinioException | InvalidKeyException
        | NoSuchAlgorithmException | IOException
        | XmlPullParserException e) {
      log.debug("Error occurred when upload file to bucket: " + e.getMessage());
      e.printStackTrace();
    }
    return isSuccess;
  }
}
