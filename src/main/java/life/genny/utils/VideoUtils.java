package life.genny.utils;

import org.apache.logging.log4j.Logger;
import ws.schild.jave.Encoder;
import ws.schild.jave.EncoderException;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.EncodingAttributes;
import ws.schild.jave.encode.VideoAttributes;
import ws.schild.jave.encode.enums.X264_PROFILE;
import ws.schild.jave.info.MultimediaInfo;
import ws.schild.jave.progress.EncoderProgressListener;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public class VideoUtils {
    protected static final Logger log = org.apache.logging.log4j.LogManager.getLogger(VideoUtils.class);
    public static File convert(File input, String videoType) throws IOException, EncoderException {
        log.debug("#### Starting video conversion");
        Instant start = Instant.now();
        String fileName = UUID.randomUUID().toString();
        File target = TemporaryFileStore.createTemporaryFile(fileName + ".mp4");

        AudioAttributes audio = new AudioAttributes();
        audio.setCodec("aac");
        audio.setBitRate(128000);
        audio.setSamplingRate(44100);
        audio.setChannels(2);
        VideoAttributes video = new VideoAttributes();
        video.setCodec("h264");
        video.setX264Profile(X264_PROFILE.BASELINE);
        video.setBitRate(10000000);
        video.setPixelFormat("yuv420p");
        video.setFrameRate(30);
        video.setFaststart(true);
        EncodingAttributes attrs = new EncodingAttributes();

        attrs.setOutputFormat(videoType);
        attrs.setAudioAttributes(audio);
        attrs.setVideoAttributes(video);

        Encoder encoder = new Encoder();
        encoder.encode(new MultimediaObject(input), target, attrs, new EncoderProgressListener() {
            @Override
            public void sourceInfo(MultimediaInfo multimediaInfo) {
                log.debug("#### Source Duration:" + multimediaInfo.getDuration());
                log.debug("#### Source Format: " + multimediaInfo.getFormat());
                log.debug("#### Source Encoder: " + multimediaInfo.getMetadata().get("encoder"));
            }

            @Override
            public void progress(int i) {
                log.debug("#### Progress State: " + i);
            }

            @Override
            public void message(String s) {
            }
        });

        Instant end = Instant.now();
        Duration timeElapsed = Duration.between(start, end);
        log.debug("#### Time taken to convert: " + timeElapsed.toMillis() + " milliseconds");
        log.debug("#### Ended video conversion");
        return target;
    }
}
