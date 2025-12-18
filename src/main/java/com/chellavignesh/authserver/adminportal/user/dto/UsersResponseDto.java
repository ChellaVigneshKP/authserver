package com.chellavignesh.authserver.adminportal.user.dto;

import java.util.List;

public record UsersResponseDto(
        Integer offset,
        Integer resultsPerPage,
        Integer results,
        List<UserResponseDto> users
) {

    public static UsersResponseDto from(
            Integer offset,
            Integer resultsPerPage,
            Integer results,
            List<UserResponseDto> users
    ) {
        return new UsersResponseDto(
                offset,
                resultsPerPage,
                results,
                users
        );
    }
}
