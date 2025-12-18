package com.chellavignesh.authserver.adminportal.user.dto;

import com.chellavignesh.authserver.enums.entity.SuffixType;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@JsonInclude(Include.NON_NULL)
public class UpdateUserDto extends NotifiableUpdateDtoBase {

    private String firstName;
    private String lastName;
    private String title;
    private String middleInitial;
    private String phoneNumber;
    private SuffixType suffix;
    private UUID memberId;
    private UUID loginId;
    private String secondaryPhoneNumber;
}
