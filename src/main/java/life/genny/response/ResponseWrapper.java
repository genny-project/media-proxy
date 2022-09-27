package life.genny.response;

import java.io.Serializable;

public class ResponseWrapper implements Serializable {

    private Boolean success;
    private String description;
    private Object data;

    public Boolean getSuccess() {
        return success;
    }

    public ResponseWrapper success(Boolean success) {
        this.success = success;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public ResponseWrapper description(String description) {
        this.description = description;
        return this;
    }

    public Object getData() {
        return data;
    }

    public ResponseWrapper data(Object data) {
        this.data = data;
        return this;
    }
}
