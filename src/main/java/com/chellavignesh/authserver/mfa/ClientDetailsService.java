package com.chellavignesh.authserver.mfa;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class ClientDetailsService {

    public static final String CLIENT_ID = "clientId";
    public static final String CLIENT_SECRET = "clientSecret";

    private final Environment env;

    @Autowired
    public ClientDetailsService(Environment env) {
        this.env = env;
    }

    public Map<String, String> getClientDetails(Integer realmId) {

        String clientId = env.getProperty("secureauth.api.access." + realmId + ".clientId");
        String clientSecret = env.getProperty("secureauth.api.access." + realmId + ".clientSecret");

        if (StringUtils.isEmpty(clientId) || StringUtils.isEmpty(clientSecret)) {
            clientId = env.getProperty("secureauth.api.access.1.clientId");
            clientSecret = env.getProperty("secureauth.api.access.1.clientSecret");
        }

        if (StringUtils.isEmpty(clientId) || StringUtils.isEmpty(clientSecret)) {
            clientId = env.getProperty("secureauth.api.access.clientId");
            clientSecret = env.getProperty("secureauth.api.access.clientSecret");
        }

        Map<String, String> clientDetails = new HashMap<>();
        clientDetails.put(CLIENT_ID, clientId);
        clientDetails.put(CLIENT_SECRET, clientSecret);

        return clientDetails;
    }
}
