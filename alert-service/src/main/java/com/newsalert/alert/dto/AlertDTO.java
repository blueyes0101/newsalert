package com.newsalert.alert.dto;

import com.newsalert.alert.entity.Alert;

import java.time.LocalDateTime;

public class AlertDTO {

    public Long id;
    public String keyword;
    public boolean active;
    public LocalDateTime createdAt;

    public static AlertDTO from(Alert a) {
        AlertDTO dto = new AlertDTO();
        dto.id = a.id;
        dto.keyword = a.keyword;
        dto.active = a.active;
        dto.createdAt = a.createdAt;
        return dto;
    }
}
