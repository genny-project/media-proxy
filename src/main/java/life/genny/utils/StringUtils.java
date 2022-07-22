package life.genny.utils;

import java.util.Arrays;
import java.util.UUID;

public class StringUtils {
    /**
     * Example:
     * fileName:  blob.mp4
     * return: 87a67f50-cce7-47fc-b00d-c008c0c36c6f.mp4
     *
     * @param fileName
     * @return
     */
    public static String fileNameToUuid(String fileName) {
        System.out.println("#### Original filename: " + fileName);
        String[] splitted = fileName.split("\\.");
        System.out.println("#### Splitted filename: " + Arrays.toString(splitted));
        String extension = splitted[splitted.length - 1];
        String outputFileName = UUID.randomUUID().toString() + "." + extension;
        System.out.println("#### Converted filename: " + outputFileName);
        return outputFileName;
    }
}
