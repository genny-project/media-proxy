package life.genny.response;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class VideoConversionResponse implements Serializable {
    private String videoId;
    private Map<String, Boolean> qualities = new HashMap<>();

    public VideoConversionResponse put(String type, Boolean completed){
        this.qualities.put(type,completed);
        return this;
    }


    public VideoConversionResponse videoId(String videoId) {
        this.videoId = videoId;
        return this;
    }

    public String getVideoId() {
        return videoId;
    }

    public Map<String, Boolean> getQualities() {
        return qualities;
    }

    @Override
    public String toString() {
        return "VideoDTO{" +
                "videoId='" + videoId + '\'' +
                ", qualities=" + qualities +
                '}';
    }
}
