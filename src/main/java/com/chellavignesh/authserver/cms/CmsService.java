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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

@Service
public class CmsService {

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${cms.file.location}")
    private String fileLocation;

    private static final Logger log = LoggerFactory.getLogger(CmsService.class);

    private final ApplicationService applicationService;

    public CmsService(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    private String createFileName(String branding, Optional<String> optCmsContext) {
        return optCmsContext.map(cmsContext -> branding + "." + cmsContext + ".json").orElse(branding + ".json");
    }

    private Map<String, String> parse(String fileName) throws CmsFileNotFoundException, IOException {

        File cmsFile = new File(fileLocation + "/" + fileName);

        if (!cmsFile.exists() || !cmsFile.isFile()) {
            log.error("The file: {}/ {} could not be found", fileLocation, fileName);
            throw new CmsFileNotFoundException("The file " + fileName + " could not be found");
        }

        Map<String, String> cmsData;

        try {
            cmsData = objectMapper.readValue(cmsFile, new TypeReference<>() {
            });
        } catch (IOException e) {
            log.error("Error while reading CMS file");
            throw e;
        }

        return cmsData;
    }

    private String getCmsContext(String clientId) {
        return Optional.ofNullable(clientId).flatMap(applicationService::getByClientId).flatMap(application -> applicationService.getApplicationDetailById(application.getId())).map(ApplicationDetail::getCmsContext).orElse(null);
    }

    private Map<String, String> getCmsInfo(String branding, Optional<String> optCmsContext) throws CmsFileNotFoundException, CmsProcessingException {

        try {
            return parse(createFileName(branding, optCmsContext));
        } catch (CmsFileNotFoundException | IOException e) {

            if (optCmsContext.isPresent()) {

                try {
                    log.info("File with CMS Context not found. Trying with file {}/{}.json", fileLocation, branding);

                    return parse(createFileName(branding, Optional.empty()));

                } catch (IOException _) {
                    throw new CmsProcessingException("Error while parsing json file");
                }

            } else {

                if (e instanceof CmsFileNotFoundException) {
                    throw (CmsFileNotFoundException) e;
                } else {
                    throw new CmsProcessingException("Error while parsing json file");
                }
            }
        }
    }

    public Map<String, String> getCmsInfoForRequest(HttpServletRequest request) throws CmsFileNotFoundException, CmsBadRequestException, CmsProcessingException {

        var brandingSessionInfo = request.getSession().getAttribute(ApplicationConstants.BRANDING_INFO);

        if (brandingSessionInfo == null) {
            throw new CmsBadRequestException("Session missing branding information");
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
