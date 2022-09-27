package life.genny.utils;

import life.genny.constants.VideoConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ws.schild.jave.Encoder;
import ws.schild.jave.EncoderException;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.EncodingAttributes;
import ws.schild.jave.encode.VideoAttributes;
import ws.schild.jave.info.MultimediaInfo;
import ws.schild.jave.progress.EncoderProgressListener;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

public class VideoUtils {
    private static Logger log = LoggerFactory.getLogger(VideoUtils.class);
    private static Encoder encoder = new Encoder();

    public static File convert(String fileName, File input, String videoType, Integer videoBitrate) throws IOException, EncoderException {
        log.debug(" Starting video conversion for: "+ fileName);
        Instant start = Instant.now();
        File target = TemporaryFileStore.createTemporaryFile(fileName);
        AudioAttributes audio = new AudioAttributes();
        audio.setCodec(VideoConstants.Config.audioCodec);
        audio.setBitRate(VideoConstants.Config.audioBitRate);
        audio.setSamplingRate(VideoConstants.Config.audioSamplingRate);
        audio.setChannels(VideoConstants.Config.audioChannels);
        VideoAttributes video = new VideoAttributes();
        video.setCodec(VideoConstants.Config.videoCodec);
        video.setX264Profile(VideoConstants.Config.videoX264Profile);
        video.setBitRate(videoBitrate);
        video.setPixelFormat(VideoConstants.Config.videoPixelFormat);
        video.setFaststart(VideoConstants.Config.videoFastStart);
        EncodingAttributes attrs = new EncodingAttributes();

        attrs.setOutputFormat(videoType);
        attrs.setAudioAttributes(audio);
        attrs.setVideoAttributes(video);

        encoder.encode(new MultimediaObject(input), target, attrs, new EncoderProgressListener() {
            @Override
            public void sourceInfo(MultimediaInfo multimediaInfo) {
                log.debug("Source Duration:" + multimediaInfo.getDuration());
                log.debug("Source Format: " + multimediaInfo.getFormat());
                log.debug("Source Encoder: " + multimediaInfo.getMetadata().get("encoder"));
            }

            @Override
            public void progress(int i) {
                log.debug("Progress State for " + fileName + ": " + i);
            }

            @Override
            public void message(String s) {
            }
        });

        Instant end = Instant.now();
        Duration timeElapsed = Duration.between(start, end);
        log.debug("Time taken to convert: " + timeElapsed.toMillis() + " milliseconds");
        log.debug("Ended video conversion for: "+ fileName);
        return target;
    }
}
