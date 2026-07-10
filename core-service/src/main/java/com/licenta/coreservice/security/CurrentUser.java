package com.licenta.coreservice.security;

import java.util.UUID;

public record CurrentUser(UUID id, String email, String name) {}
