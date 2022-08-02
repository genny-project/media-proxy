package life.genny.utils;

import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.UUID;

public class StringUtils {

    protected static final Logger log = org.apache.logging.log4j.LogManager.getLogger(StringUtils.class);
    /**
     * Example:
     * fileName:  blob.mp4
     * return: 87a67f50-cce7-47fc-b00d-c008c0c36c6f.mp4
     *
     * @param fileName
     * @return
     */
    public static String fileNameToUuid(String fileName) {
        log.debug("#### Original filename: " + fileName);
        String[] splitted = fileName.split("\\.");
        log.debug("#### Splitted filename: " + Arrays.toString(splitted));
        String extension = splitted[splitted.length - 1];
        String outputFileName = UUID.randomUUID().toString() + "." + extension;
        log.debug("#### Converted filename: " + outputFileName);
        return outputFileName;
    }
}
