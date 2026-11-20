package com.capacitydesk.audit.application;

import com.capacitydesk.audit.domain.AuditAction;
import com.capacitydesk.audit.domain.AuditEvent;
import com.capacitydesk.audit.domain.AuditResult;
import com.capacitydesk.audit.domain.TargetType;
import com.capacitydesk.audit.persistence.AuditEventRepository;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class AuditService {

    public static final String CORRELATION_KEY = "correlationId";

    private final AuditEventRepository events;
    private final JsonMapper json;

    public AuditService(AuditEventRepository events, JsonMapper json) {
        this.events = events;
        this.json = json;
    }

    /** Joins the caller's transaction, so the audit row commits or rolls back with the business change. */
    @Transactional
    public void record(AuditAction action, TargetType type, String targetId, AuditResult result,
                       Object before, Object after, String detail) {
        events.save(build(action, type, targetId, result, before, after, detail));
    }

    /** For events where the actor is not yet in the security context (successful login). */
    @Transactional
    public void recordAs(UUID actorId, String actorName, AuditAction action, TargetType type, String targetId) {
        events.save(new AuditEvent(actorId, actorName, action, type, targetId, AuditResult.SUCCESS,
                MDC.get(CORRELATION_KEY), null, null, null));
    }

    public void success(AuditAction action, TargetType type, Object targetId, Object before, Object after) {
        record(action, type, String.valueOf(targetId), AuditResult.SUCCESS, before, after, null);
    }

    /** Own transaction: survives rollback of the caller. Use for failures and denied attempts. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependent(AuditAction action, TargetType type, String targetId, AuditResult result,
                                  Object before, Object after, String detail) {
        events.save(build(action, type, targetId, result, before, after, detail));
    }

    private AuditEvent build(AuditAction action, TargetType type, String targetId, AuditResult result,
                             Object before, Object after, String detail) {
        UUID actorId = null;
        String actorName = "system";
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            actorId = UUID.fromString(jwt.getSubject());
            actorName = jwt.getClaimAsString("username");
        } else if (auth instanceof AnonymousAuthenticationToken) {
            actorName = "anonymous";
        }
        String d = detail == null ? null : detail.substring(0, Math.min(detail.length(), 500));
        return new AuditEvent(actorId, actorName, action, type, targetId, result, MDC.get(CORRELATION_KEY),
                serialize(before), serialize(after), d);
    }

    private String serialize(Object o) {
        return o == null ? null : json.writeValueAsString(o);
    }
}
