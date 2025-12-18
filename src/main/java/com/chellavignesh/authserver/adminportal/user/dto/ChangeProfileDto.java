package com.chellavignesh.authserver.adminportal.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChangeProfileDto {

    private String firstName;
    private String lastName;
    private String email;
    private String confirmEmail;
    private String phoneNumber;
    private String secondaryPhoneNumber;

    public static ChangeProfileDto fromUpdateUserDto(UpdateUserDto dto) {
        ChangeProfileDto changeProfileDto = new ChangeProfileDto();
        changeProfileDto.firstName = dto.getFirstName();
        changeProfileDto.lastName = dto.getLastName();
        changeProfileDto.phoneNumber = dto.getPhoneNumber();
        changeProfileDto.secondaryPhoneNumber = dto.getSecondaryPhoneNumber();
        return changeProfileDto;
    }
}