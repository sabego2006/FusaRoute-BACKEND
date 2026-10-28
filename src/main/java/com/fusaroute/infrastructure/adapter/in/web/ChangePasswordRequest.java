package com.fusaroute.infrastructure.adapter.in.web;

/** Cuerpo del PUT /api/users/me/password. */
public record ChangePasswordRequest(String currentPassword, String newPassword) {
}
