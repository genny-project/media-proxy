package life.genny;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.UUID;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParserException;
import com.amazonaws.util.IOUtils;
import io.minio.MinioClient;
import io.minio.ObjectStat;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.InvalidArgumentException;
import io.minio.errors.InvalidBucketNameException;
import io.minio.errors.InvalidEndpointException;
import io.minio.errors.InvalidPortException;
import io.minio.errors.MinioException;
import io.minio.errors.NoResponseException;
import io.vertx.rxjava.core.MultiMap;
import io.vertx.rxjava.ext.web.FileUpload;
import io.vertx.rxjava.ext.web.RoutingContext;
import life.genny.qwandautils.KeycloakUtils;

public class Minio {

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
    System.out.println("DEBUG, authValue:" + authValue);
    String[] split = authValue.split(" ");
    System.out.println("DEBUG, split:" + split);
    String token = split[1];
    System.out.println("DEBUG, token:" + token);
    return token;
  }

  public static String extractRealm(String token) {
    JSONObject decodedToken = KeycloakUtils.getDecodedToken(token);
    String kcRealmUrl = decodedToken.get("iss").toString();
    String[] split = kcRealmUrl.split("/");
    int length = split.length;
    String realm = split[length-1];
    return realm;
  }
  public static UUID extractUserUUID(String token) {
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
    uploadFile(REALM.concat("/")+"public",file.uploadedFileName(),randomUUID.toString());
    uploadFile(REALM.concat("/")+"public",fileInfo.getPath(),randomUUID.toString().concat("-info"));
    fileInfo.delete();
    return randomUUID;

  }

  public static UUID saveOnStore(FileUpload file,UUID userUUID) {
    UUID randomUUID = UUID.randomUUID();
    uploadFile(userUUID.toString(), 
        file.uploadedFileName(),
        randomUUID.toString());
    return randomUUID;
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

  public static void uploadFile(String sub,
      String inpt, String uuid) {

    String path = sub + "/" + "media" + "/" + uuid;
    try {
      boolean isExist = minioClient.bucketExists(EnvironmentVariables.BUCKET_NAME);
      if (isExist) {
        System.out.println("Bucket already exists.");
      } else {
        System.out.println("Creating Bucket.");
        minioClient.makeBucket(EnvironmentVariables.BUCKET_NAME);
      }
      try {
        minioClient.putObject(EnvironmentVariables.BUCKET_NAME, path, inpt);
      } catch (IOException e) {
        e.printStackTrace();
      }

    } catch (MinioException | InvalidKeyException
        | NoSuchAlgorithmException | IOException
        | XmlPullParserException e) {
      System.out.println("Error occurred: " + e);

    }
  }


}
