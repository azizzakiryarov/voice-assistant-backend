package com.voiceassistant.exception;

public class FormScanException extends RuntimeException {
    public FormScanException(String message) {
        super(message);
    }

    public FormScanException(String message, Throwable cause) {
        super(message, cause);
    }
}
