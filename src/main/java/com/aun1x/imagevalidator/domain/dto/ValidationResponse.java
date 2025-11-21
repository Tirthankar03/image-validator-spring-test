package com.aun1x.imagevalidator.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for image validation response.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResponse {
    private String status;
    private String message;
    private String suggestion;
    private Double effectiveDpi;
    private Double widthPct;
    private Double heightPct;

    public ValidationResponse(String error, String s, Object o, Object o1, Object o2, Object o3) {
    }
}