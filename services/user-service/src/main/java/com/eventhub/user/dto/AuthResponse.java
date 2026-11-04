package com.eventhub.user.dto;

public class AuthResponse {
    private String token;
    private String tokenType = "Bearer";
    private UserProfileDTO user;

    public AuthResponse() {}

    public AuthResponse(String token, UserProfileDTO user) {
        this.token = token;
        this.user = user;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getTokenType() { return tokenType; }
    public void setTokenType(String tokenType) { this.tokenType = tokenType; }

    public UserProfileDTO getUser() { return user; }
    public void setUser(UserProfileDTO user) { this.user = user; }
}
