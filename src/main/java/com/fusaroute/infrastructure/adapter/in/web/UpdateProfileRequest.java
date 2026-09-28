package com.fusaroute.infrastructure.adapter.in.web;

public record UpdateProfileRequest(
    String name,
    String email,
    String phone
) {}
