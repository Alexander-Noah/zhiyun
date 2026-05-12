package org.example.backend.result;

import java.util.Collections;

import lombok.Data;

@Data
public class Result {
    private static final Integer SUCCESS_CODE = 200;
    private static final Integer ERROR_CODE = -1;
    private static final String SUCCESS_MESSAGE = "success";

    private Integer code;
    private String message;
    private Object data;

    public static Result success() {
        return success(Collections.emptyMap());
    }

    public static Result success( Object data) {
        Result result = new Result();
        result.setCode(SUCCESS_CODE);
        result.setMessage(SUCCESS_MESSAGE);
        result.setData(data);
        return result;
    }
    public static Result success(String message, Object data){
        Result result = new Result();
        result.setCode(SUCCESS_CODE);
        result.setMessage(message);
        result.setData(data);
        return result;
    }

    public static Result error(String message) {
        return error(ERROR_CODE, message);
    }

    public static Result error(Integer code, String message) {
        Result result = new Result();
        result.setCode(code);
        result.setMessage(message);
        result.setData(Collections.emptyMap());
        return result;
    }
}
