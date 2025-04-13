package com.olehprukhnytskyi.macrotrackeruserservice.validation.validator;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.RegisterRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.validation.PasswordMatches;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordMatchesValidator implements
        ConstraintValidator<PasswordMatches, RegisterRequestDto> {
    @Override
    public void initialize(PasswordMatches constraintAnnotation) {
    }

    @Override
    public boolean isValid(RegisterRequestDto registerRequestDto,
                           ConstraintValidatorContext context) {
        if (registerRequestDto.getPassword() == null
                || registerRequestDto.getConfirmPassword() == null) {
            return false;
        }
        return registerRequestDto.getPassword()
                .equals(registerRequestDto.getConfirmPassword());
    }
}
