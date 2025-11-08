package com.chellavignesh.authserver.adminportal.organization.dto;

import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

@Component
public class CreateOrganizationDtoValidator implements Validator {

    @Override
    public boolean supports(Class<?> clazz) {
        return CreateOrganizationDto.class.equals(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        CreateOrganizationDto dto = (CreateOrganizationDto) target;
        if (dto.getName() == null || dto.getName().isEmpty()) {
            errors.rejectValue("name", "Invalid", "Organization name must be provided");
        }
    }
}
