package life.genny;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParserException;
import com.amazonaws.util.IOUtils;
import io.minio.MinioClient;
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

  static {
    try {
      minioClient =
          new MinioClient(EnvironmentVariables.MINIO_SERVER_URL,
              EnvironmentVariables.MINIO_ACCESS_KEY,
              EnvironmentVariables.MINIO_PRIVATE_KEY);
    } catch (InvalidEndpointException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (InvalidPortException e) {
      // TODO Auto-generated catch block
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
    uploadFile("public",file.uploadedFileName(),randomUUID.toString());
    uploadFile("public",fileInfo.getPath(),randomUUID.toString().concat("-info"));
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
      // TODO Auto-generated catch block
      e.printStackTrace();
      return new byte[] {};
    }
  }

  public static String fetchInfoFromStorePublicDirectory(UUID fileUUID) {
    try {
      InputStream object = minioClient.getObject(EnvironmentVariables.BUCKET_NAME,
           "public" + "/" + "media" + "/"+ fileUUID.toString().concat("-info"));
      byte[] byteArray = IOUtils.toByteArray(object);
      return new String(byteArray);
    } catch (InvalidKeyException | InvalidBucketNameException
        | NoSuchAlgorithmException | InsufficientDataException
        | NoResponseException | ErrorResponseException
        | InternalException | InvalidArgumentException | IOException
        | XmlPullParserException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      return "";
    }
  }
  public static byte[] fetchFromStorePublicDirectory(UUID fileUUID) {
    try {
      InputStream object = minioClient.getObject(EnvironmentVariables.BUCKET_NAME,
           "public" + "/" + "media" + "/"+ fileUUID.toString());
      byte[] byteArray = IOUtils.toByteArray(object);
      return byteArray;
    } catch (InvalidKeyException | InvalidBucketNameException
        | NoSuchAlgorithmException | InsufficientDataException
        | NoResponseException | ErrorResponseException
        | InternalException | InvalidArgumentException | IOException
        | XmlPullParserException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      return new byte[] {};
    }
  }

  public static void deleteFromStorePublicDirectory(UUID fileUUID) {
    try {
      minioClient.removeObject(EnvironmentVariables.BUCKET_NAME,
           "public" + "/" + "media" + "/"+ fileUUID.toString());
    } catch (InvalidKeyException | InvalidBucketNameException
        | NoSuchAlgorithmException | InsufficientDataException
        | NoResponseException | ErrorResponseException
        | InternalException | InvalidArgumentException | IOException
        | XmlPullParserException e) {
      // TODO Auto-generated catch block
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
