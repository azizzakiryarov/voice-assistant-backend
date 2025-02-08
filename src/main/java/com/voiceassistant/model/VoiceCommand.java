package com.voiceassistant.model;

public class VoiceCommand {
    private String command;

    public VoiceCommand() {
    }

    public VoiceCommand(String command) {
        this.command = command;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }
}
