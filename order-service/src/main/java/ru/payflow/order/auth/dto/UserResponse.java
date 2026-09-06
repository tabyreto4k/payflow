package ru.payflow.order.auth.dto;

import java.util.UUID;
import ru.payflow.order.auth.model.Role;
import ru.payflow.order.auth.model.User;

public record UserResponse(UUID id, String email, Role role) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole());
    }
}
