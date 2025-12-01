package com.chellavignesh.authserver.unite.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@AllArgsConstructor
@ToString
public class UniteErrorDto {
    private String code;
    private String message;
    private String field;
}
