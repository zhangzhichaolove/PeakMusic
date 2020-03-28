package com.chao.peakmusic.base;

/**
 * Created by Chao  2018/3/9 on 11:48
 * description
 */
public class HttpResult<T> {
    private boolean success;
    private String msg;
    private T result;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getResult() {
        return result;
    }

    public void setResult(T result) {
        this.result = result;
    }
}
