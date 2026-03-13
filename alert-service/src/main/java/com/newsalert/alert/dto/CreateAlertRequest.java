package com.newsalert.alert.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateAlertRequest {

    @NotBlank
    @Size(min = 2, max = 255, message = "Keyword must be between 2 and 255 characters")
    public String keyword;
}
