package life.genny.utils;

import java.io.File;
import java.io.IOException;

public class TemporaryFileStore {

    public static File createTemporaryFile(String fileName) throws IOException {
        File tempFile = new File("/tmp/" + fileName);
        if (tempFile.exists()) {
            tempFile.delete();
        }
        return tempFile;
    }
}
