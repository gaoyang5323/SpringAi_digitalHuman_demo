package com.kakuiwong.gaoyangspringai.common;

import lombok.Data;

/**
 * @author: gaoyang
 * @Description: 统一响应对象，data 为业务对象
 */
@Data
public class Result<T> {

    private boolean success;

    private String message;

    private T data;

    public static <T> Result<T> ok(T data) {
        Result<T> result = new Result<>();
        result.success = true;
        result.data = data;
        return result;
    }

    public static <T> Result<T> fail(String message) {
        Result<T> result = new Result<>();
        result.success = false;
        result.message = message;
        return result;
    }
}