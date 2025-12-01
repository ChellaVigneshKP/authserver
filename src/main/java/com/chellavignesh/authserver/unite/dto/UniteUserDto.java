package com.chellavignesh.authserver.unite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collection;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UniteUserDto {
    private String username;
    private String memberId;
    private String uuidMemberId;
    private Collection<UniteErrorDto> errors = new ArrayList<>();
}
