package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.model.User;

public record UserProfileResponse(
    String name,
    String email,
    String phone
) {
    public static UserProfileResponse fromDomain(User user) {
        return new UserProfileResponse(user.getName(), user.getEmail().value(), user.getPhone());
    }
}
