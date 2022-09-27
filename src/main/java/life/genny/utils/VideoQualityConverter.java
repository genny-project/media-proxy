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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class VideoQualityConverter {
    private static final Logger log = LoggerFactory.getLogger(VideoQualityConverter.class);
    private static final ExecutorService executors = Executors.newFixedThreadPool(100, VideoQualityConverter::createThreadFactory);
    private static int count = 1;

    private static Thread createThreadFactory(Runnable runnable) {
        Thread thread = new Thread(runnable);
        thread.setName("videoconverter-" + thread.getName().toLowerCase());
        return thread;
    }

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

        if (count == 1) {
            count++;
            Boolean is360Completed = convert(input, mp4Video360FileName, quality.getInteger("360"));
            Boolean is720Completed = convert(input, mp4Video720FileName, quality.getInteger("720"));

            VideoConversionResponse videoConversionResponse  = new VideoConversionResponse()
                    .videoId(fileUUID)
                    .put("360p", is360Completed)
                    .put("720p", is720Completed);

            Boolean completed = checkIfAllConverted(videoConversionResponse.getQualities());
            input.delete();
            return new ResponseWrapper().data(videoConversionResponse).description(completed ? "Success" : "Failure").success(completed);
        } else {
            count++;
            CompletableFuture<Boolean> task360p = CompletableFuture
                    .supplyAsync(() -> convert(input, mp4Video360FileName, quality.getInteger("360")), executors);

            CompletableFuture<Boolean> task720p = CompletableFuture
                    .supplyAsync(() -> convert(input, mp4Video720FileName, quality.getInteger("720")), executors);

            ResponseWrapper responseWrapper =  CompletableFuture.allOf(task360p, task720p)
                    .thenApply(v -> {
                        VideoConversionResponse response = new VideoConversionResponse()
                                .videoId(fileUUID)
                                .put("360p", task360p.join())
                                .put("720p", task720p.join());

                        return response;
                    }).thenApply(videoConversionResponse -> {
                        Boolean completed = checkIfAllConverted(videoConversionResponse.getQualities());
                        return new ResponseWrapper().data(videoConversionResponse).description(completed ? "Success" : "Failure").success(completed);
                    }).join();

            input.delete();
            return responseWrapper;

        }

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