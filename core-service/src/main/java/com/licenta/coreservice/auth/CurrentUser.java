package com.licenta.coreservice.auth;

import java.util.UUID;

public record CurrentUser(UUID id, String email, String name) {}
