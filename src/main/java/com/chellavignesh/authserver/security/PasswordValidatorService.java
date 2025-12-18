package com.chellavignesh.authserver.security;

import com.chellavignesh.authserver.adminportal.user.UserService;
import com.chellavignesh.authserver.adminportal.user.entity.UserDetails;
import com.chellavignesh.authserver.config.OutputMessagesConstants;
import com.chellavignesh.authserver.security.exception.PasswordBlackListedException;
import com.chellavignesh.authserver.security.exception.PasswordIncorrectSemanticException;
import com.chellavignesh.authserver.security.exception.PasswordInvalidSyntaxException;
import com.chellavignesh.authserver.security.exception.PasswordRecentlyUsedException;
import com.chellavignesh.authserver.security.passwordvalidator.repository.PasswordHistoryRepository;
import com.chellavignesh.authserver.security.passwordvalidator.repository.entity.PasswordHistoryData;
import com.chellavignesh.authserver.session.HasherConfig;
import com.chellavignesh.authserver.session.KPCVPasswordEncoder;
import com.chellavignesh.authserver.session.LibCryptoPasswordEncoder;
import com.chellavignesh.libcrypto.service.impl.CryptoWebClientImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.thymeleaf.util.StringUtils;

import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.containsIgnoreCase;

@Service
public class PasswordValidatorService {

    private final PasswordHistoryRepository passwordHistoryRepository;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final UserService userService;
    private final Pattern passwordPatternValidator;
    private final CryptoWebClientImpl cryptoWebClient;
    private final HasherConfig hasherConfig;

    public PasswordValidatorService(
            @Value("${validation.password.regex}") String passwordValidationRegExp,
            NamedParameterJdbcTemplate namedParameterJdbcTemplate,
            PasswordHistoryRepository passwordHistoryRepository, UserService userService, CryptoWebClientImpl cryptoWebClient, HasherConfig hasherConfig
    ) {
        this.passwordHistoryRepository = passwordHistoryRepository;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.userService = userService;
        this.cryptoWebClient = cryptoWebClient;
        this.hasherConfig = hasherConfig;
        this.passwordPatternValidator = Pattern.compile(passwordValidationRegExp);
    }

    /**
     * Complete set of password validation: syntax, semantic, blacklist and recent usages
     */
    public boolean validatePassword(String password, UUID userGuid)
            throws PasswordBlackListedException,
            PasswordInvalidSyntaxException,
            PasswordIncorrectSemanticException,
            PasswordRecentlyUsedException {

        UserDetails data = userService.getByGuid(userGuid).orElseThrow();

        return validatePassword(password)
                && validSemantic(password, data.username(), data.firstName(), data.lastName(), data.email())
                && passwordNotBlackListed(password)
                && validateRecentUsages(password, userGuid);
    }

    /**
     * Basic validation for password strings and blacklist control
     */
    public boolean validatePassword(String password)
            throws PasswordInvalidSyntaxException, PasswordBlackListedException {

        return validatePasswordBasicRegexp(password) && passwordNotBlackListed(password);
    }

    public boolean validateRecentUsages(String password, UUID userGuid)
            throws PasswordRecentlyUsedException {

        UserDetails data = userService.getByGuid(userGuid).orElseThrow();

        List<PasswordHistoryData> passwordHistoryData = passwordHistoryRepository.getHistoricPasswords(data.id());
        if (passwordHistoryData.isEmpty()) {
            return true;
        }

        KPCVPasswordEncoder ascensusEncoder = new KPCVPasswordEncoder(hasherConfig);
        LibCryptoPasswordEncoder libCryptoPasswordEncoder = new LibCryptoPasswordEncoder(cryptoWebClient);

        // TODO: This base64 encode is going to be a problem until the WAF rule allows all characters in a password.
        String encodedPassword = Base64.getEncoder().encodeToString(password.getBytes());

        for (PasswordHistoryData row : passwordHistoryData) {

            if (row.getVersion() == KPCVPasswordEncoder.ENCODER_ID
                    && ascensusEncoder.matches(encodedPassword, row.getPassword())) {
                throw new PasswordRecentlyUsedException(OutputMessagesConstants.PASSWORD_RECENT_USAGE);
            }

            if (row.getVersion() == LibCryptoPasswordEncoder.ENCODER_ID
                    && libCryptoPasswordEncoder.matches(encodedPassword, row.getPassword())) {
                throw new PasswordRecentlyUsedException(OutputMessagesConstants.PASSWORD_RECENT_USAGE);
            }
        }

        return true;
    }

    public boolean passwordNotBlackListed(String password)
            throws PasswordBlackListedException {

        var parameters = new MapSqlParameterSource();
        parameters.addValue("password", password);

        boolean validation = Boolean.TRUE.equals(namedParameterJdbcTemplate.queryForObject(
                "{call Person.validatePasswordBlacklisted(:password)}",
                parameters,
                Boolean.class
        ));

        if (!validation) {
            throw new PasswordBlackListedException(OutputMessagesConstants.PASSWORD_BLACKLISTED);
        }

        return validation;
    }

    public static boolean validSemantic(
            String password,
            String username,
            String firstName,
            String lastName,
            String email) throws PasswordIncorrectSemanticException {

        boolean firstNameValidation = containsIgnoreCase(password, firstName);
        boolean lastNameValidation = containsIgnoreCase(password, lastName);
        boolean emailValidation = containsIgnoreCase(password, email);
        boolean usernameValidation = containsIgnoreCase(password, username);

        if (firstNameValidation || lastNameValidation || emailValidation || usernameValidation) {
            throw new PasswordIncorrectSemanticException(OutputMessagesConstants.INVALID_PASSWORD_USAGE);
        }

        return true;
    }

    public boolean validatePasswordBasicRegexp(String password)
            throws PasswordInvalidSyntaxException {

        if (StringUtils.isEmpty(password)
                || !passwordPatternValidator.matcher(password).matches()) {
            throw new PasswordInvalidSyntaxException(OutputMessagesConstants.INVALID_PASSWORD_SYNTAX);
        }

        return true;
    }
}
