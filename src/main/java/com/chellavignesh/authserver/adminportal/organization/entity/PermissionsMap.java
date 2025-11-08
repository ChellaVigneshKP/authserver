package com.chellavignesh.authserver.adminportal.organization.entity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PermissionsMap {
    public static Map<String, List<String>> getPermissionsMap(List<OrganizationGroupPermission> organizationGroupPermissionList) {
        Map<String, List<String>> permissionMap = new HashMap<>();
        for (OrganizationGroupPermission permission : organizationGroupPermissionList) {
            String organicGroupPermissionKey = permission.getKey();
            String[] splitKey = organicGroupPermissionKey.split(":");
            if (splitKey.length != 2) {
                throw new RuntimeException("Error parsing permission key: " + organicGroupPermissionKey);
            }
            String permissionName = splitKey[0];
            String permissionVerb = splitKey[1];
            if (permissionMap.containsKey(permissionName)) {
                List<String> actions = permissionMap.get(permissionName);
                if (!actions.contains(permissionVerb)) {
                    permissionMap.put(permissionName, Stream.concat(actions.stream(), Stream.of(permissionVerb))
                            .map(String::valueOf)
                            .collect(Collectors.toList()));
                }
            } else {
                permissionMap.put(permissionName, Stream.of(permissionVerb).toList());
            }
        }
        return permissionMap;
    }

    public static Map<String, List<String>> getPermissionsMapFromPermissionStrings(String[] permissionsList) {
        Map<String, List<String>> permissionMap = new HashMap<>();
        for (String permission : permissionsList) {
            String[] splitKey = permission.split(":");
            if (splitKey.length != 2) {
                if (splitKey.length == 3) {
                    splitKey[1] = splitKey[1] + ":" + splitKey[2];
                } else {
                    throw new RuntimeException("Error parsing permission key: " + permission);
                }
            }
            String permissionName = splitKey[0];
            String permissionVerb = splitKey[1];
            if (permissionMap.containsKey(permissionName)) {
                List<String> actions = permissionMap.get(permissionName);
                if (!actions.contains(permissionVerb)) {
                    permissionMap.put(permissionName, Stream.concat(actions.stream(), Stream.of(permissionVerb))
                            .map(String::valueOf)
                            .collect(Collectors.toList()));
                }
            } else {
                permissionMap.put(permissionName, Stream.of(permissionVerb).toList());
            }
        }
        return permissionMap;
    }
}
