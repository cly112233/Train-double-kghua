package com.kghua.npcai.webbridge;

/** 命令处理器业务异常：带错误码返回给后端（E_OFFLINE/E_VALIDATION/E_PERMISSION/E_NOT_FOUND） */
public class WebException extends Exception {
    public final String code;

    public WebException(String code, String msg) {
        super(msg);
        this.code = code;
    }
}
