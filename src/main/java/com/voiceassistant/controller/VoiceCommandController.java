package com.voiceassistant.controller;

import com.voiceassistant.model.VoiceCommand;
import com.voiceassistant.service.OpenAIService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/voice")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class VoiceCommandController {

    private OpenAIService openAIService;


    @POST
    public Response processVoiceCommand(VoiceCommand command) {
        return openAIService.processCommand(command);
    }
}