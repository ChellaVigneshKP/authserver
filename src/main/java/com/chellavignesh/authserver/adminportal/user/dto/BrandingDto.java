package com.chellavignesh.authserver.adminportal.user.dto;

import java.util.List;
import java.util.Optional;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

@Data
@NoArgsConstructor
public class BrandingDto {

    private List<String> brandingIds;

    @NotNull
    public List<String> getBrandingIds() {
        return Optional.ofNullable(brandingIds).orElseGet(List::of);
    }
}
