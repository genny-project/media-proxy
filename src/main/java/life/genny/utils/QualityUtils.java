package life.genny.utils;

import java.util.HashMap;
import java.util.Map;

public class QualityUtils {
    public static Map<String, Integer> quality = new HashMap<>();

    static {
        quality.put("360", 800000);
        quality.put("720", 2500000);
    }
}
