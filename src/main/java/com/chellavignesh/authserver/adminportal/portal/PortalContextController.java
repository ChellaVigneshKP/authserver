package com.chellavignesh.authserver.adminportal.portal;

import com.chellavignesh.authserver.adminportal.portal.dto.PortalContextDto;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/portal")
public class PortalContextController {
    private final PortalContextService contexts;
    public PortalContextController(PortalContextService contexts) { this.contexts = contexts; }

    @GetMapping("/context")
    public PortalContextDto context(Authentication authentication) { return contexts.getContext(authentication); }
}
