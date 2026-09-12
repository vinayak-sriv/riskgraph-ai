package ai.riskgraph.platform.security;

import ai.riskgraph.platform.service.PipelineException;
import ai.riskgraph.platform.service.ScanStore;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScanAccessService {
    public enum Access { VIEW, VALIDATE }

    private final JdbcTemplate db;
    private final ScanStore scans;
    private final AccountService accounts;
    private final Map<String, Map<String, Access>> local = new ConcurrentHashMap<>();

    public ScanAccessService(ObjectProvider<JdbcTemplate> database, ScanStore scans, AccountService accounts) {
        this.db = database.getIfAvailable();
        this.scans = scans;
        this.accounts = accounts;
    }

    @Transactional
    public void claim(String scanId) {
        String username = current().getName();
        put(scanId, username, Access.VALIDATE, true);
    }

    public void requireView(String scanId) {
        require(scanId, Access.VIEW);
    }

    public void requireValidation(String scanId) {
        require(scanId, Access.VALIDATE);
    }

    @Transactional
    public void grant(String scanId, String username, Access access) {
        require(scanId, Access.VALIDATE);
        if (scans.get(scanId) == null) {
            throw new PipelineException("SCAN_NOT_FOUND", 404, "Scan not found");
        }
        try {
            accounts.profile(username);
        } catch (UsernameNotFoundException error) {
            throw new PipelineException("ACCOUNT_NOT_FOUND", 404, "Account not found");
        }
        put(scanId, username, access, false);
    }

    private void require(String scanId, Access required) {
        Authentication authentication = current();
        if (isAdmin(authentication)) return;
        Access granted = get(scanId, authentication.getName());
        boolean allowed = granted == Access.VALIDATE || required == Access.VIEW && granted == Access.VIEW;
        if (!allowed) {
            // Avoid revealing whether an unshared scan identifier exists.
            throw new PipelineException("SCAN_ACCESS_DENIED", 403, "This scan has not been shared with your account");
        }
    }

    private Access get(String scanId, String username) {
        if (db == null) return local.getOrDefault(scanId, Map.of()).get(username);
        var values = db.queryForList("""
                SELECT access_level FROM scan_memberships m
                JOIN scans s ON s.id=m.scan_id JOIN users u ON u.id=m.user_id
                WHERE s.external_id=? AND u.username=? AND u.enabled
                """, String.class, scanId, username);
        return values.isEmpty() ? null : Access.valueOf(values.getFirst());
    }

    private void put(String scanId, String username, Access access, boolean preserveValidation) {
        if (db == null) {
            if (preserveValidation) {
                local.computeIfAbsent(scanId, ignored -> new ConcurrentHashMap<>()).merge(
                        username, access, (oldAccess, newAccess) ->
                                oldAccess == Access.VALIDATE ? oldAccess : newAccess);
            } else {
                local.computeIfAbsent(scanId, ignored -> new ConcurrentHashMap<>()).put(username, access);
            }
            return;
        }
        int updated = db.update("""
                INSERT INTO scan_memberships(scan_id,user_id,access_level)
                SELECT s.id,u.id,? FROM scans s CROSS JOIN users u
                WHERE s.external_id=? AND u.username=? AND u.enabled
                ON CONFLICT (scan_id,user_id) DO UPDATE SET
                    access_level=CASE WHEN ? AND scan_memberships.access_level='VALIDATE'
                        THEN 'VALIDATE' ELSE excluded.access_level END,
                    updated_at=now()
                """, access.name(), scanId, username, preserveValidation);
        if (updated != 1) {
            throw new PipelineException("SCAN_ACCESS_TARGET_INVALID", 404, "Scan or account not found");
        }
    }

    private Authentication current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new PipelineException("AUTHENTICATION_REQUIRED", 401, "Authentication is required");
        }
        return authentication;
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }
}
