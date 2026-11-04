package com.eventhub.user.controller;

import com.eventhub.user.dto.*;
import com.eventhub.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "user-service",
                "stack", "Java 21 / Spring Boot 3",
                "port", 8081,
                "requirements", List.of("RF01", "RN07", "RQ07")
        ));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(userService.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileDTO> getMyProfile(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("authenticatedUserId");
        return ResponseEntity.ok(userService.getMyProfile(userId));
    }

    @PutMapping("/me")
    public ResponseEntity<UserProfileDTO> updateMyProfile(
            HttpServletRequest request,
            @Valid @RequestBody UpdateProfileRequest updateRequest
    ) {
        Long userId = (Long) request.getAttribute("authenticatedUserId");
        return ResponseEntity.ok(userService.updateMyProfile(userId, updateRequest));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserProfileDTO> getUserById(
            @PathVariable Long id,
            HttpServletRequest request
    ) {
        Long authenticatedUserId = (Long) request.getAttribute("authenticatedUserId");
        String authenticatedRole = (String) request.getAttribute("authenticatedRole");
        return ResponseEntity.ok(userService.getUserById(id, authenticatedUserId, authenticatedRole));
    }

    @GetMapping("/demo-accounts")
    public ResponseEntity<List<UserProfileDTO>> listDemoAccounts() {
        return ResponseEntity.ok(userService.listDemoAccounts());
    }
}
