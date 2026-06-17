package com.voiceassistant.exception;

public class TextAnalysisException extends RuntimeException {
    public TextAnalysisException(String message) {
        super(message);
    }

    public TextAnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
}
