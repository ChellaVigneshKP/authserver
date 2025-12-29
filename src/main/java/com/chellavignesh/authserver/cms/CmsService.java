package com.chellavignesh.authserver.cms;

import com.chellavignesh.authserver.adminportal.application.ApplicationService;
import com.chellavignesh.authserver.adminportal.application.entity.ApplicationDetail;
import com.chellavignesh.authserver.cms.exception.CmsBadRequestException;
import com.chellavignesh.authserver.cms.exception.CmsFileNotFoundException;
import com.chellavignesh.authserver.cms.exception.CmsProcessingException;
import com.chellavignesh.authserver.config.ApplicationConstants;
import com.chellavignesh.authserver.session.entity.AuthSession;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

@Service
public class CmsService {
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    @Value("${cms.file.location}")
    private String fileLocation;

    private static final Logger log = LoggerFactory.getLogger(CmsService.class);

    private final ApplicationService applicationService;

    public CmsService(ApplicationService applicationService, ObjectMapper objectMapper, ResourceLoader resourceLoader) {
        this.applicationService = applicationService;
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
    }

    private String createFileName(String branding, Optional<String> cmsContext) {
        return cmsContext
                .filter(StringUtils::isNotBlank)
                .map(ctx -> branding + "." + ctx + ".json")
                .orElse(branding + ".json");
    }


    private Map<String, String> parse(String fileName)
            throws CmsFileNotFoundException, CmsProcessingException {

        try {
            Resource resource = resourceLoader.getResource(fileLocation + fileName);

            if (!resource.exists()) {
                log.error("CMS file not found: {}{}", fileLocation, fileName);
                throw new CmsFileNotFoundException("The file " + fileName + " could not be found");
            }

            try (InputStream is = resource.getInputStream()) {
                return objectMapper.readValue(is, new TypeReference<>() {
                });
            }

        } catch (IOException e) {
            log.error("Error while reading CMS file", e);
            throw new CmsProcessingException("Error while parsing json file");
        }
    }

    private String getCmsContext(String clientId) {
        return Optional.ofNullable(clientId).flatMap(applicationService::getByClientId).flatMap(application -> applicationService.getApplicationDetailById(application.getId())).map(ApplicationDetail::getCmsContext).orElse(null);
    }

    private Map<String, String> getCmsInfo(String branding, Optional<String> optCmsContext) throws CmsFileNotFoundException, CmsProcessingException {

        try {
            return parse(createFileName(branding, optCmsContext));
        } catch (CmsFileNotFoundException e) {

            if (optCmsContext.isPresent()) {

                log.info("File with CMS Context not found. Trying with file {}/{}.json", fileLocation, branding);

                return parse(createFileName(branding, Optional.empty()));

            } else {
                throw e;
            }
        }
    }

    public Map<String, String> getCmsInfoForRequest(HttpServletRequest request) throws CmsFileNotFoundException, CmsBadRequestException, CmsProcessingException {

        var brandingSessionInfo = request.getSession().getAttribute(ApplicationConstants.BRANDING_INFO);

        if (brandingSessionInfo == null) {
            log.warn("Session missing branding information, using default branding");
            // Use a default branding value instead of throwing exception
            // This allows the application to work even when branding is not set
            brandingSessionInfo = ApplicationConstants.DEFAULT_BRANDING;
        }

        Optional<String> clientId = Optional.ofNullable((String) request.getSession().getAttribute(ApplicationConstants.CLIENT_ID));

        Optional<String> cmsContext = clientId.map(this::getCmsContext);

        return getCmsInfo((String) brandingSessionInfo, cmsContext);
    }

    public Map<String, String> getCmsInfoFromSession(AuthSession authSession) throws CmsFileNotFoundException, CmsProcessingException {

        String branding = authSession.getBranding();

        Optional<String> cmsContext = applicationService.getApplicationDetailById(authSession.getApplicationId()).map(ApplicationDetail::getCmsContext);

        return getCmsInfo(branding, cmsContext);
    }
}
