package com.chellavignesh.authserver.adminportal.forgotusername;

import com.chellavignesh.authserver.adminportal.forgotusername.entity.UsernameLookupCriteria;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class ForgotUsernameSetting {
    private List<UsernameLookupCriteria> settings;

    public String toJSONString() throws JsonProcessingException {
        ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
        List<String[]> searchCriteria = this.settings.stream().map(UsernameLookupCriteria::getLookupCriteria).toList();
        return ow.writeValueAsString(searchCriteria);
    }
}
