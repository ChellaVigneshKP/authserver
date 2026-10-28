package com.chellavignesh.authserver.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.http.HttpResponse;
import java.nio.file.*;
import java.util.*;

/** Optional real-transport evidence. Never serializes requests, responses or secret columns. */
public final class ManagementObservations {
    private ManagementObservations() {}
    @FunctionalInterface public interface Request { HttpResponse<String> send() throws Exception; }
    private static final Map<String,String> PROJECTIONS = Map.ofEntries(
            Map.entry("Partner.Organization", "OrganizationId,Name,Note,Status,PrimaryContactName,PrimaryContactEmail,PrimaryContactPhoneNumber,SecondaryContactName,SecondaryContactEmail,SecondaryContactPhoneNumber"),
            Map.entry("Partner.OrganizationGroup", "OrganizationGroupId,OrganizationId,GroupName,Description,Status,Revision,IsBuiltin,GroupTemplateId"),
            Map.entry("Partner.OrganizationGroupPermission", "OrganizationGroupPermissionId,OrganizationGroupId,OrganizationId,PermissionKey,Status"),
            Map.entry("Person.Profile", "ProfileId,FirstName,LastName,Email,PhoneNumber,TwoFactorEnabled,Status"),
            Map.entry("Person.Credential", "Id,ProfileId,UserName,Status,Version,ModifiedOn"),
            Map.entry("Person.ProfileOrganization", "ProfileOrganizationId,ProfileId,OrganizationId,Status"),
            Map.entry("Person.ProfileGroup", "ProfileGroupId,ProfileId,OrganizationGroupId"),
            Map.entry("Person.Metadata", "MetadataId,ProfileId,[Key],[Value]"),
            Map.entry("Client.Application", "ApplicationId,OrganizationId,Name,Description,Active,Uri,ApplicationTypeId,AuthFlowId"),
            Map.entry("Client.Credential", "CredentialId,ApplicationId,OrganizationId,Name,CredentialStatus,ExpireOn"),
            Map.entry("Client.RedirectUri", "RedirectUriId,OrganizationId,ApplicationId,RedirectUri"),
            Map.entry("Client.PostLogoutRedirectUri", "PostLogoutRedirectUriId,OrganizationId,ApplicationId,PostLogoutRedirectUri"),
            Map.entry("Client.TokenSetting", "TokenSettingId,ApplicationId,AuthCodeTimeToLive,AccessTokenTimeToLive,RefreshTokenTimeToLive,ReuseRefreshTokens,MaxRequestTransitTime"),
            Map.entry("Partner.Certificate", "CertificateId,OrganizationId,CertificateName,CertificateTypeId,Status,ValidFrom,ValidTo"),
            Map.entry("dbo.ResourceLibrary", "ResourceLibraryId,Name,Description,Uri,AllowedMethod,Urn,Status"),
            Map.entry("Resource.Resource", "ResourceId,OrganizationId,ApplicationId,ResourceLibraryId,Status"));

    public static Map<String,List<Map<String,Object>>> snapshot(JdbcTemplate sql) {
        String database = sql.queryForObject("SELECT DB_NAME()", String.class);
        if (database == null || !database.matches("Authserver(?:Management|Policy)_[0-9a-f]{32}"))
            throw new IllegalStateException("Observation readback requires this test's owned database");
        var state = new TreeMap<String,List<Map<String,Object>>>();
        PROJECTIONS.forEach((table, columns) -> state.put(table,
                sql.queryForList("SELECT "+columns+" FROM "+table+" ORDER BY "+columns.split(",")[0])));
        return state;
    }

    public static String actor(JdbcTemplate sql, String subject) {
        var rows = sql.queryForList("""
                SELECT po.OrganizationId FROM Person.Credential c
                JOIN Person.ProfileOrganization po ON po.ProfileId=c.ProfileId AND po.Status=1
                WHERE c.UserName=?
                """, Integer.class, subject);
        if (rows.isEmpty()) return "machine";
        return rows.contains(1) ? "human_platform_organization" : "human_tenant";
    }

    public static HttpResponse<String> capture(JdbcTemplate sql, ObjectMapper mapper, String scenario,
                                               String actor, String method, String path, Request request) throws Exception {
        String destination = System.getenv("AUTH_MANAGEMENT_OBSERVATIONS");
        if (destination == null) return request.send();
        var before = snapshot(sql);
        var response = request.send();
        var after = snapshot(sql);
        var changed = before.keySet().stream().filter(table -> !before.get(table).equals(after.get(table))).toList();
        var counts = new TreeMap<String,Integer>();
        after.forEach((table, rows) -> counts.put(table, rows.size()));
        var event = new LinkedHashMap<String,Object>();
        event.put("observed_at_utc", java.time.Instant.now().toString());
        event.put("scenario", scenario);
        event.put("actor_class", actor);
        event.put("method", method);
        // Only route structure survives: no GUIDs, query values, headers or payloads.
        event.put("url", "/services"+path.split("\\?", 2)[0].replaceAll("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "{id}"));
        event.put("status", response.statusCode());
        event.put("sql_outcome", Map.of("readback", "completed", "projection", "public structural fields only",
                "changed_tables", changed, "row_counts", counts));
        event.put("source_commit", System.getenv("AUTH_MANAGEMENT_SOURCE_COMMIT"));
        event.put("source_tree_sha256", System.getenv("AUTH_MANAGEMENT_SOURCE_TREE"));
        event.put("provider_simulation", false);
        event.put("provider", "not_exercised");
        synchronized (ManagementObservations.class) {
            Files.writeString(Path.of(destination), mapper.writeValueAsString(event)+"\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        return response;
    }
}
