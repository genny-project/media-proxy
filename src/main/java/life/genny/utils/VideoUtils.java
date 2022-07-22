package life.genny.utils;

import life.genny.qwandautils.JsonUtils;
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
import java.util.UUID;

public class VideoUtils {

    public static File convert(File input, String videoType) throws IOException, EncoderException {
        System.out.println("#### Starting video conversion");
        Instant start = Instant.now();
        String fileName = UUID.randomUUID().toString();
        File target = TemporaryFileStore.createTemporaryFile(fileName + ".mp4");

        AudioAttributes audio = new AudioAttributes();
        audio.setCodec("eac3");
        audio.setBitRate(128000);
        audio.setSamplingRate(44100);
        audio.setChannels(2);
        VideoAttributes video = new VideoAttributes();
        video.setCodec("mpeg4");
        video.setBitRate(12000000);
        video.setFrameRate(30);
        EncodingAttributes attrs = new EncodingAttributes();

        attrs.setOutputFormat(videoType);
        attrs.setAudioAttributes(audio);
        attrs.setVideoAttributes(video);

        Encoder encoder = new Encoder();
        encoder.encode(new MultimediaObject(input), target, attrs, new EncoderProgressListener() {
            @Override
            public void sourceInfo(MultimediaInfo multimediaInfo) {
                System.out.println("#### Source Duration:" + multimediaInfo.getDuration());
                System.out.println("#### Source Format: " + multimediaInfo.getFormat());
                System.out.println("#### Source Encoder: " + multimediaInfo.getMetadata().get("encoder"));
            }

            @Override
            public void progress(int i) {
                System.out.println("#### Progress State: " + i);
            }

            @Override
            public void message(String s) {
            }
        });

        Instant end = Instant.now();
        Duration timeElapsed = Duration.between(start, end);
        System.out.println("#### Time taken to convert: " + timeElapsed.toMillis() + " milliseconds");
        System.out.println("#### Ended video conversion");
        return target;
    }
}
