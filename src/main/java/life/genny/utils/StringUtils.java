package life.genny.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.UUID;

public class StringUtils {

    private static final Logger log = LoggerFactory.getLogger(StringUtils.class);
    /**
     * Example:
     * fileName:  blob.mp4
     * return: 87a67f50-cce7-47fc-b00d-c008c0c36c6f.mp4
     *
     * @param fileName
     * @return
     */
    public static String fileNameToUuid(String fileName) {
        log.debug("Original filename: " + fileName);
        String[] splitted = fileName.split("\\.");
        log.debug("Splitted filename: " + Arrays.toString(splitted));
        String extension = splitted[splitted.length - 1];
        String outputFileName = UUID.randomUUID().toString() + "." + extension;
        log.debug("Converted filename: " + outputFileName);
        return outputFileName;
    }
}
