package com.fusaroute.infrastructure.adapter.in.web;

public record ChangePasswordRequest(
    String currentPassword,
    String newPassword
) {}
