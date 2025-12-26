package com.chellavignesh.authserver.config;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.springframework.security.core.GrantedAuthority;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(
                value = PreAuthGrantedAuthority.class,
                name = "preAuthGrantedAuthority"
        )
})
public class PreAuthGrantedAuthority implements GrantedAuthority {

    private static final String PRE_AUTH = "PRE_AUTH";

    @Override
    public String getAuthority() {
        return PRE_AUTH;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof PreAuthGrantedAuthority other)) {
            return false;
        }
        return PRE_AUTH.equals(other.getAuthority());
    }

    @Override
    public int hashCode() {
        return PRE_AUTH.hashCode();
    }
}

