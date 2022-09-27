package life.genny.constants;

import ws.schild.jave.encode.enums.X264_PROFILE;

public interface VideoConstants {

    String suffix720p = "-720p";
    String suffix360p = "-360p";

    interface Config {
        String audioCodec = "aac";
        Integer audioBitRate = 128000;
        Integer audioSamplingRate = 44100;
        Integer audioChannels = 2;
        String videoCodec = "h264";
        X264_PROFILE videoX264Profile = X264_PROFILE.BASELINE;
        String videoPixelFormat = "yuv420p";
        Boolean videoFastStart = true;
    }
}
