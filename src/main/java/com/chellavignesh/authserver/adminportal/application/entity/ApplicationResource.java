package com.chellavignesh.authserver.adminportal.application.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriTemplate;
import org.thymeleaf.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ApplicationResource {
    private Integer id;
    private UUID rowGuid;
    private Integer orgId;
    private Integer appId;
    private Integer resourceLibraryId;
    private String name;
    private String description;
    private String uri;
    private String allowedMethod;
    private String urn;

    public static ApplicationResource fromResultSet(ResultSet result) throws SQLException {
        ApplicationResource resource = new ApplicationResource();
        resource.setId(result.getInt("ResourceId"));
        resource.setRowGuid(UUID.fromString(result.getString("ResourceLibraryGuid")));
        resource.setOrgId(result.getInt("OrganizationId"));
        resource.setAppId(result.getInt("ApplicationId"));
        resource.setResourceLibraryId(result.getInt("ResourceLibraryId"));
        resource.setName(result.getString("Name"));
        resource.setDescription(result.getString("Description"));
        resource.setUri(result.getString("Uri"));
        resource.setAllowedMethod(result.getString("AllowedMethod"));
        resource.setUrn(result.getString("Urn"));
        return resource;
    }

    public boolean compareResource(String uri, String allowedMethod, String urn) {
        return getAllowedMethod().equals(allowedMethod)
                && compareUri(this.getUri(), uri)
                && (StringUtils.isEmpty(urn) ? (Objects.equals(getUrn(), urn) || getUrn() == null) : Objects.equals(getUrn(), urn));
    }

    public boolean compareUri(String url1, String url2) {
        try {
            var uri1 = UriComponentsBuilder.fromHttpUrl(url1).build();
            var uri2 = UriComponentsBuilder.fromHttpUrl(url2).build();
            return compareHosts(uri1, uri2) && comparePaths(uri1, uri2);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean compareHosts(UriComponents uri1, UriComponents uri2) {
        String host1 = uri1.getHost();
        String host2 = uri2.getHost();
        return host1 == null || host2 == null || host1.equals(host2);
    }

    private boolean comparePaths(UriComponents uri1, UriComponents uri2) {
        String path1 = uri1.getPath();
        String path2 = uri2.getPath();
        if (Objects.isNull(path1) || Objects.isNull(path2)) {
            return Objects.equals(path1, path2);
        }
        UriTemplate template = new UriTemplate(path1);
        return template.matches(path2) && comparePathParameters(template, path2);
    }

    private boolean comparePathParameters(UriTemplate template, String uri) {
        return template.match(uri).entrySet().stream()
                .allMatch((entrySet -> {
                    var regex = Pattern.compile(entrySet.getKey());
                    return regex.matcher(entrySet.getValue()).matches();
                }));
    }

}
