package com.chellavignesh.authserver.adminportal.resource.dto;

import com.chellavignesh.authserver.adminportal.util.ValidEnum;
import com.chellavignesh.authserver.adminportal.util.ValidUri;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ResourceLibraryDto {

    static final String URN_REGEX = "^urn:[a-z0-9][a-z0-9-]{0,31}:[a-z0-9()+,.:=@$_!*'/%?-]+$";

    @NotEmpty(message = "name is required")
    private String name;

    @NotEmpty(message = "description is required")
    private String description;

    @NotEmpty(message = "uri is required")
    @ValidUri(message = "uri is not in a valid URI format")
    private String uri;

    @NotEmpty(message = "allowedMethod is required")
    @ValidEnum(enumClass = HttpMethodEnum.class, message = "Invalid value for allowedMethod")
    private String allowedMethod;

    @Pattern(regexp = URN_REGEX, message = "Invalid URN format (urn:<NAMESPACE-IDENTIFIER>:<NAMESPACE-SPECIFIC-STRING>)")
    private String urn;

    public HttpMethodEnum getAllowedMethodEnum() {
        return HttpMethodEnum.valueOf(HttpMethodEnum.class, allowedMethod);
    }
}
