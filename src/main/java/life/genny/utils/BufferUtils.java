package life.genny.utils;


import io.vertx.rxjava.core.buffer.Buffer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class BufferUtils {

    public static Buffer fileToBuffer(File file) throws IOException {
        byte[] data = Files.readAllBytes(file.toPath());
        Buffer buffer = Buffer.buffer();
        for (byte e : data) {
            buffer.appendByte(e);
        }
        return buffer;
    }
}
