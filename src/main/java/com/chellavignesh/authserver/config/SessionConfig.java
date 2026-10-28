package com.chellavignesh.authserver.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.session.data.redis.config.ConfigureRedisAction;

/** Boot owns the single session repository and applies namespace, timeout and save mode. */
@Configuration
public class SessionConfig {
    @Bean
    public ConfigureRedisAction configureRedisAction() {
        return ConfigureRedisAction.NO_OP;
    }

    @Bean("springSessionDefaultRedisSerializer")
    public RedisSerializer<Object> springSessionDefaultRedisSerializer() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModules(SecurityJackson2Modules.getModules(getClass().getClassLoader()));
        mapper.registerModule(new JavaTimeModule());
        mapper.registerModule(new Jdk8Module());
        var types = com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("java.util.")
                .allowIfSubType("java.time.")
                .allowIfSubType(String.class).allowIfSubType(Long.class).allowIfSubType(Integer.class)
                .allowIfSubType(Boolean.class).allowIfSubType(Double.class)
                .allowIfSubType(byte[].class).allowIfSubType(String[].class)
                .allowIfSubType("org.springframework.security.")
                .allowIfSubType(com.chellavignesh.authserver.session.dto.CustomUserDetails.class)
                .allowIfSubType(com.chellavignesh.authserver.adminportal.user.entity.UserAuthDetails.class)
                .allowIfSubType(com.chellavignesh.authserver.adminportal.user.dto.ChangeProfileDto.class)
                .allowIfSubType(PreAuthGrantedAuthority.class).build();
        mapper.activateDefaultTyping(types, ObjectMapper.DefaultTyping.NON_FINAL,
                com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new GenericJackson2JsonRedisSerializer(mapper);
    }
}
