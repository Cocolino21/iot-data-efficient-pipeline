package com.licenta.coreservice.auth;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository repo;

    public UserService(UserRepository repo) {
        this.repo = repo;
    }

    public CurrentUser upsertFromOidc(String provider, String providerId, String email, String name) {
        UserRepository.UserRow row = repo.findByEmail(email).orElse(null);
        UUID userId;
        String resolvedName;
        if (row == null) {
            resolvedName = name != null ? name : email;
            userId = repo.insert(resolvedName, email, provider, providerId);
        } else {
            userId = row.uuid();
            resolvedName = row.name();
            repo.updateProviderId(userId, provider, providerId);
        }
        return new CurrentUser(userId, email, resolvedName);
    }
}
