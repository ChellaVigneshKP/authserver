package com.chellavignesh.authserver.adminportal.forgotusername;

import com.chellavignesh.authserver.adminportal.forgotusername.entity.UsernameLookupField;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UsernameLookupFieldService {
    private final UsernameLookupFieldRepository fieldRepository;

    public UsernameLookupFieldService(UsernameLookupFieldRepository fieldRepository) {
        this.fieldRepository = fieldRepository;
    }

    public List<UsernameLookupField> getAll() {
        return fieldRepository.getAll();
    }

    public Map<String, UsernameLookupField> getAllAsMap() {
        var fieldMap = new HashMap<String, UsernameLookupField>();
        List<UsernameLookupField> fields = this.getAll();
        for (UsernameLookupField field : fields) {
            fieldMap.put(field.getName(), field);
        }
        return fieldMap;
    }
}
