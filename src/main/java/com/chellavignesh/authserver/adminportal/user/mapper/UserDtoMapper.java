package com.chellavignesh.authserver.adminportal.user.mapper;

import com.chellavignesh.authserver.adminportal.user.dto.CreateUserDto;
import com.chellavignesh.authserver.adminportal.user.dto.CreateUserProfileDto;
import org.bouncycastle.util.encoders.Hex;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.factory.Mappers;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

@Mapper(nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public abstract class UserDtoMapper {

    @Mappings({
            @Mapping(target = "orgId", ignore = true),
            @Mapping(source = "branding", target = "branding"),
            @Mapping(source = "orgId", target = "orgGuid"),
            @Mapping(source = "credential.loginId", target = "loginId"),
            @Mapping(source = "credential.username", target = "username"),
            @Mapping(source = "profile.memberId", target = "memberId"),
            @Mapping(source = "profile.firstName", target = "firstName"),
            @Mapping(source = "profile.lastName", target = "lastName"),
            @Mapping(source = "profile.email", target = "email"),
            @Mapping(source = "profile.phoneNumber", target = "phoneNumber"),
            @Mapping(source = "profile.secondaryPhoneNumber", target = "secondaryPhoneNumber"),
            @Mapping(source = "syncFlag", target = "syncFlag")})
    public abstract CreateUserDto toCreateUserDto(CreateUserProfileDto userProfileDto);

    public static final UserDtoMapper Instance = Mappers.getMapper(UserDtoMapper.class);

    @AfterMapping
    protected void hashPassword(CreateUserProfileDto source, @MappingTarget CreateUserDto target) {
        String passwordHash = source.getCredential().getPasswordHash();
        String cIndex = source.getCredential().getcIndex();

        String hashedPassword = assemblePassword(passwordHash, cIndex);
        target.setPassword(hashedPassword);
    }

    public static String assemblePassword(String passwordHash, String cIndex) {

        byte[] hashBytes = Hex.decode(passwordHash);
        byte[] cIndexBytes = ByteBuffer.allocate(4).putInt(Integer.parseInt(cIndex)).array();

        byte[] passwordBytes = new byte[cIndexBytes.length + hashBytes.length];

        System.arraycopy(cIndexBytes, 0, passwordBytes, 0, cIndexBytes.length);
        System.arraycopy(hashBytes, 0, passwordBytes, cIndexBytes.length, hashBytes.length);

        byte[] password = Hex.encode(passwordBytes);
        return new String(password, StandardCharsets.UTF_8);
    }
}
