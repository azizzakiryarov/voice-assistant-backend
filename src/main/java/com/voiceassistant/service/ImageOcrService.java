package com.voiceassistant.service;

import java.nio.file.Path;

public interface ImageOcrService {
    String extractText(Path imagePath, String contentType);
}
