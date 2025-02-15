package com.voiceassistant.exception;

public class AudioTranslationException extends RuntimeException {
    public AudioTranslationException(String message) {
        super(message);
    }

    public AudioTranslationException(String message, Throwable cause) {
        super(message, cause);
    }
}