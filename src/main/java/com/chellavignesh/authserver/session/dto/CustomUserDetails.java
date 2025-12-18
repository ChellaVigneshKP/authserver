package com.chellavignesh.authserver.session.dto;

import com.chellavignesh.authserver.adminportal.user.entity.UserAuthDetails;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;


@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = CustomUserDetails.class, name = "customUserDetails")
})
public class CustomUserDetails implements UserDetails {

    @Getter
    UserAuthDetails userAuthDetails;

    public CustomUserDetails() {
    }

    public CustomUserDetails(UserAuthDetails userAuthDetails) {
        this.userAuthDetails = userAuthDetails;
    }

    @Setter
    private List<GrantedAuthority> authorities;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return new ArrayList<>();
    }

    @Override
    public String getPassword() {
        // Spring needs the encoded password to be prefixed with the ID of the encoder used
        return "{" + this.userAuthDetails.getVersion() + "}" + this.userAuthDetails.getPassword();
    }

    @Override
    public String getUsername() {
        return this.userAuthDetails.getUserName();
    }

    @Override
    public boolean isAccountNonExpired() {
        // User state alone determines if user account is active
        return this.userAuthDetails.getUserStatus() == 1;
    }

    @Override
    public boolean isAccountNonLocked() {
        return this.userAuthDetails.getUserStatus() == 1;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return this.userAuthDetails.getUserStatus() == 1;
    }

    @Override
    public boolean isEnabled() {
        return this.userAuthDetails.getUserStatus() == 1;
    }

    public boolean isTwoFactorEnabled() {
        return this.userAuthDetails.getTwoFactorEnabled();
    }

    public Date getLastLogin() {
        return this.userAuthDetails.getLastLogin();
    }
}
