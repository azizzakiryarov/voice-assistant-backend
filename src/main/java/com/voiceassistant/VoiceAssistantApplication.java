package com.voiceassistant;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain
public class VoiceAssistantApplication {
    public static void main(String[] args) {
        Quarkus.run(args);
    }
}