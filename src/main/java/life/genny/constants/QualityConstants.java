package life.genny.constants;

import java.util.HashMap;
import java.util.Map;

public class QualityConstants {

    public static Map<String, Integer> quality = new HashMap<>();

    static {
        quality.put("360", 800000);
        quality.put("720", 2500000);
    }
}
