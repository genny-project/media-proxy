package life.genny.utils;

import io.vertx.rxjava.ext.web.FileUpload;
import life.genny.Minio;
import life.genny.constants.VideoConstants;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConvertUtils {
    private static final Logger log = LoggerFactory.getLogger(ConvertUtils.class);

    public static void saveWithMultipleQualities(UUID uuid, FileUpload fileUpload) {
        try {
            String fileUUID = uuid.toString();
            String mp4Video720FileName = fileUUID + VideoConstants.suffix720p;
            String mp4Video360FileName = fileUUID + VideoConstants.suffix360p;
            File input = new File(fileUpload.uploadedFileName());
            File target360 = VideoUtils.convert(mp4Video360FileName, input, "mp4", QualityUtils.quality.get("360"));
            log.debug("#### Converted to 360p quality");
            Minio.saveOnStore(mp4Video360FileName, target360);
            target360.delete();
            log.debug("#### Saved to 360p quality");
            File target720 = VideoUtils.convert(mp4Video720FileName, input, "mp4", QualityUtils.quality.get("720"));
            log.debug("#### Converted to 720p quality");
            Minio.saveOnStore(mp4Video720FileName, target720);
            target720.delete();
            log.debug("#### Saved to 720p quality");
            input.delete();
        } catch (Exception ex) {
            log.error("Exception: " + ex.getMessage());
        }
    }

    public static void convert(String fileUUID, byte[] inputByteData) {
        try {
            String mp4Video720FileName = fileUUID + VideoConstants.suffix720p;
            String mp4Video360FileName = fileUUID + VideoConstants.suffix360p;
            File input = TemporaryFileStore.createTemporaryFile(fileUUID);
            FileUtils.writeByteArrayToFile(input, inputByteData);

            File target360 = VideoUtils.convert(mp4Video360FileName, input, "mp4", QualityUtils.quality.get("360"));
            log.debug("#### Converted to 360p quality");
            Minio.saveOnStore(mp4Video360FileName, target360);
            target360.delete();
            log.debug("#### Saved to 360p quality");
            File target720 = VideoUtils.convert(mp4Video720FileName, input, "mp4", QualityUtils.quality.get("720"));
            log.debug("#### Converted to 720p quality");
            Minio.saveOnStore(mp4Video720FileName, target720);
            target720.delete();
            log.debug("#### Saved to 720p quality");
            input.delete();
        } catch (Exception ex) {
            log.error("Exception: " + ex.getMessage());
        }
    }
}
