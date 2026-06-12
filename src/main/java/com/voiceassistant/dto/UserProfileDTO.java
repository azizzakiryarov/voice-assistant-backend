package com.voiceassistant.dto;

import lombok.Data;

@Data
public class UserProfileDTO {
    private Long id;
    private String email;
    private String name;
    private String pictureUrl;
    private boolean authenticated;
}
