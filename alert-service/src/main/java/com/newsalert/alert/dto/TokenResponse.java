package com.newsalert.alert.dto;

public class TokenResponse {

    public String token;
    public String type = "Bearer";

    public TokenResponse(String token) {
        this.token = token;
    }
}
