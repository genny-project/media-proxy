package life.genny.utils;

import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.ext.web.FileUpload;
import life.genny.ApplicationConfig;
import life.genny.MinIO;
import life.genny.constants.VideoConstants;
import life.genny.response.ResponseWrapper;
import life.genny.response.VideoConversionResponse;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;


public class VideoQualityConverter {
    private static final Logger log = LoggerFactory.getLogger(VideoQualityConverter.class);
    private static String successResponse = "Success";
    private static String failureResponse = "Failure";


    public static ResponseWrapper convert(String uuid, FileUpload fileUpload) {
        try {
            byte[] inputByteData = Files.readAllBytes(Paths.get(fileUpload.uploadedFileName()));
            return convert(uuid.toString(), inputByteData);
        } catch (Exception ex) {
            log.error("Exception: " + ex.getMessage());
            return new ResponseWrapper().description("Failure").success(false);
        }
    }

    public static ResponseWrapper convert(UUID uuid, FileUpload fileUpload) {
        try {
            byte[] inputByteData = Files.readAllBytes(Paths.get(fileUpload.uploadedFileName()));
            return convert(uuid.toString(), inputByteData);
        } catch (Exception ex) {
            log.error("Exception: " + ex.getMessage());
            return new ResponseWrapper().description("Failure").success(false);
        }
    }

    public static ResponseWrapper convert(String fileUUID, byte[] inputByteData) throws Exception {
        String mp4Video720FileName = fileUUID + VideoConstants.suffix720p;
        String mp4Video360FileName = fileUUID + VideoConstants.suffix360p;
        JsonObject quality = ApplicationConfig.getConfig().getJsonObject("video").getJsonObject("quality");
        File input = TemporaryFileStore.createTemporaryFile(fileUUID);

        FileUtils.writeByteArrayToFile(input, inputByteData);

        Boolean is360Completed = convert(input, mp4Video360FileName, quality.getInteger("360"));
        Boolean is720Completed = convert(input, mp4Video720FileName, quality.getInteger("720"));

        VideoConversionResponse videoConversionResponse = new VideoConversionResponse()
                .videoId(fileUUID)
                .put("360p", is360Completed)
                .put("720p", is720Completed);

        Boolean completed = checkIfAllConverted(videoConversionResponse.getQualities());

        input.delete();

        return new ResponseWrapper().data(videoConversionResponse).description(completed ? successResponse : failureResponse).success(completed);
    }


    public static Boolean checkIfAllConverted(Map<String, Boolean> map) {
        for (Map.Entry<String, Boolean> entry : map.entrySet()) {
            if (!entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    public static Boolean convert(File input, String fileName, Integer bitRate) {
        try {
            fileName = fileName.concat(".mp4");
            File target = VideoUtils.convert(fileName, input, "mp4", bitRate);
            log.debug("Converted: " + fileName);
            MinIO.saveOnStore(fileName, target);
            log.debug("Saved: " + fileName);
            target.delete();
            return true;
        } catch (Exception e) {
            log.debug("Exception occurred with: " + fileName);
            log.debug("Exception: " + e.getMessage());
            return false;
        }
    }
}