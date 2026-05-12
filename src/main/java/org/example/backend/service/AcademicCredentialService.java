package org.example.backend.service;

import org.example.backend.entity.AcademicCredentialView;
import org.example.backend.entity.AcademicScheduleImportRequest;

public interface AcademicCredentialService {
    AcademicCredentialView saveCredential(AcademicScheduleImportRequest request);

    AcademicCredentialView getCredentialView(String credentialKey);

    void deleteCredential(String credentialKey);

    void applySavedCredential(AcademicScheduleImportRequest request);
}
