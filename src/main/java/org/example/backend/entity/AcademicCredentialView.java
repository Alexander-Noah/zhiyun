package org.example.backend.entity;

import lombok.Data;

@Data
public class AcademicCredentialView {
    private String credentialKey;
    private String usernameMasked;
    private Boolean configured;
    private String updatedAt;
}
