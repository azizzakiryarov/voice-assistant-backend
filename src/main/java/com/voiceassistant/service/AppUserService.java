package com.voiceassistant.service;

import com.voiceassistant.dto.UserProfileDTO;
import com.voiceassistant.model.AppUser;
import com.voiceassistant.repository.AppUserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AppUserService {

    private final AppUserRepository appUserRepository;

    public AppUserService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Transactional
    public AppUser createOrUpdate(OAuth2User oauth2User) {
        String subject = oauth2User.getAttribute("sub");
        if (subject == null || subject.isBlank()) {
            throw new IllegalStateException("Google profile is missing subject");
        }

        AppUser appUser = appUserRepository.findByGoogleSubject(subject).orElseGet(AppUser::new);
        appUser.setGoogleSubject(subject);
        appUser.setEmail(oauth2User.getAttribute("email"));
        appUser.setName(oauth2User.getAttribute("name"));
        appUser.setPictureUrl(oauth2User.getAttribute("picture"));
        appUser.setLastLoginAt(Instant.now());
        return appUserRepository.save(appUser);
    }

    public AppUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof OAuth2User oauth2User)) {
            throw new IllegalStateException("User is not authenticated");
        }
        String subject = oauth2User.getAttribute("sub");
        return appUserRepository.findByGoogleSubject(subject)
                .orElseGet(() -> createOrUpdate(oauth2User));
    }

    public UserProfileDTO currentProfile() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserProfileDTO profile = new UserProfileDTO();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof OAuth2User oauth2User)) {
            profile.setAuthenticated(false);
            return profile;
        }

        AppUser appUser = appUserRepository.findByGoogleSubject(oauth2User.getAttribute("sub"))
                .orElseGet(() -> createOrUpdate(oauth2User));
        profile.setAuthenticated(true);
        profile.setId(appUser.getId());
        profile.setEmail(appUser.getEmail());
        profile.setName(appUser.getName());
        profile.setPictureUrl(appUser.getPictureUrl());
        return profile;
    }
}
