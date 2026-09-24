package ai.riskgraph.platform.security;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ai.riskgraph.platform.service.PipelineException;

@Service
public class AccountService implements UserDetailsService, ApplicationRunner {
    public enum Role {
        DEVELOPER("Developer"), ANALYST("Security Analyst"), ADMIN("Admin");
        final String label;
        Role(String label) { this.label = label; }
        static Role fromLabel(String label) {
            for (var role : values()) if (role.label.equals(label)) return role;
            throw new IllegalArgumentException("Invalid stored role");
        }
    }
    public record Profile(String username, String name, Role role) {}
    public enum GithubStatus { NOT_CONFIGURED, DISCONNECTED, CONNECTED, ERROR }
    public record GithubConnection(GithubStatus status, String login, List<String> permissions, String message) {}
    private record Account(Profile profile, String hash) {}
    private final JdbcTemplate db;
    private final PasswordEncoder encoder;
    private final Map<String, Account> local = new ConcurrentHashMap<>();
    private final Map<String, String> localGithubSubjects = new ConcurrentHashMap<>();
    private final Map<String, String> localGithubLogins = new ConcurrentHashMap<>();
    private final String bootstrapFile;

    public AccountService(ObjectProvider<JdbcTemplate> database, PasswordEncoder encoder,
        @Value("${RISKGRAPH_BOOTSTRAP_PASSWORD_FILE:}") String bootstrapFile) {
        this.db = database.getIfAvailable(); this.encoder = encoder; this.bootstrapFile = bootstrapFile;
    }

    @Override public void run(ApplicationArguments args) throws Exception {
        // No default password and no reset on restart. Missing setup leaves APIs locked.
        if (list().isEmpty() && !bootstrapFile.isBlank() && Files.isRegularFile(Path.of(bootstrapFile))) {
            String password = Files.readString(Path.of(bootstrapFile)).strip();
            create("admin", "Local administrator", Role.ADMIN, password);
        }
    }

    public List<Profile> list() {
        if (db == null) return local.values().stream().map(Account::profile)
            .sorted(java.util.Comparator.comparing(Profile::username)).toList();
        return db.query("SELECT username,name,role FROM users WHERE enabled ORDER BY username",
            (row, index) -> new Profile(row.getString(1), row.getString(2), Role.fromLabel(row.getString(3))));
    }

    public synchronized Profile create(String username, String name, Role role, String password) {
        if (username == null || !username.matches("[a-z][a-z0-9_.-]{2,63}") || name == null
            || name.isBlank() || name.length() > 120 || role == null || password == null
            || password.length() < 12 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new PipelineException("INVALID_ACCOUNT", 400, "Use a valid username, role and a password of 12 to 72 UTF-8 bytes");
        if (find(username) != null) throw new PipelineException("ACCOUNT_EXISTS", 409, "Username is already registered");
        var profile = new Profile(username, name, role);
        String hash = encoder.encode(password);
        if (db == null) local.put(username, new Account(profile, hash));
        else db.update("INSERT INTO users(username,name,role,password_hash,enabled) VALUES (?,?,?,?,true)",
            username, name, role.label, hash);
        return profile;
    }

    public void setVerifiedEmail(String username, String email, boolean verified) {
        Profile profile = profile(username);
        if (db == null)
            throw new PipelineException("EMAIL_CONFIGURATION_UNAVAILABLE", 409,
                "Verified notification email requires PostgreSQL");
        String normalized = email.strip().toLowerCase(java.util.Locale.ROOT);
        db.update("UPDATE users SET email=?,email_verified=?,updated_at=now() WHERE username=? AND enabled",
            normalized, verified, profile.username());
    }

    private Account find(String username) {
        if (db == null) return local.get(username);
        var rows = db.query("SELECT username,name,role,password_hash FROM users WHERE username=? AND enabled",
            (row, index) -> new Account(new Profile(row.getString(1), row.getString(2), Role.fromLabel(row.getString(3))), row.getString(4)), username);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public Profile profile(String username) {
        var account = find(username);
        if (account == null) throw new UsernameNotFoundException("Account unavailable");
        return account.profile();
    }

    public boolean isGithubLinked(String username) {
        if (username == null) return false;
        if (db == null) return localGithubLogins.containsKey(username);
        Integer count = db.queryForObject("SELECT count(*) FROM external_identities e JOIN users u ON u.id=e.user_id WHERE e.provider='github' AND u.username=? AND u.enabled",
            Integer.class, username);
        return count != null && count > 0;
    }

    public GithubConnection githubConnection(String username, boolean configured) {
        if (!configured) return new GithubConnection(GithubStatus.NOT_CONFIGURED, null, List.of(), null);
        if (username == null) return new GithubConnection(GithubStatus.DISCONNECTED, null, List.of(), null);
        String login;
        if (db == null) login = localGithubLogins.get(username);
        else {
            var rows = db.query("SELECT e.provider_login FROM external_identities e JOIN users u ON u.id=e.user_id WHERE e.provider='github' AND u.username=? AND u.enabled",
                (row, index) -> row.getString(1), username);
            login = rows.isEmpty() ? null : rows.getFirst();
        }
        return login == null
            ? new GithubConnection(GithubStatus.DISCONNECTED, null, List.of(), null)
            : new GithubConnection(GithubStatus.CONNECTED, login,
                List.of("Identity verified", "Dashboard services unlocked"), null);
    }

    @Transactional
    public synchronized Profile connectGithub(String subject, String login, String displayName, String requestedUsername) {
        if (subject == null || !subject.matches("[0-9]{1,32}") || login == null || login.isBlank())
            throw new PipelineException("INVALID_GITHUB_IDENTITY", 400, "GitHub returned an incomplete account identity");
        String safeName = displayName == null || displayName.isBlank() ? login : displayName.strip();
        if (safeName.length() > 255) safeName = safeName.substring(0, 255);
        if (db == null) return connectGithubLocally(subject, login, safeName, requestedUsername);

        var existing = db.query("SELECT u.username,u.name,u.role FROM external_identities e JOIN users u ON u.id=e.user_id WHERE e.provider='github' AND e.provider_subject=? AND u.enabled",
            (row, index) -> new Profile(row.getString(1), row.getString(2), Role.fromLabel(row.getString(3))), subject);
        if (!existing.isEmpty()) {
            var profile = existing.getFirst();
            if (requestedUsername != null && !requestedUsername.equals(profile.username()))
                throw new PipelineException("GITHUB_IDENTITY_IN_USE", 409, "That GitHub identity is already linked to another RiskGraph account");
            db.update("UPDATE external_identities SET provider_login=?,updated_at=now() WHERE provider='github' AND provider_subject=?", login, subject);
            db.update("UPDATE users SET name=?,updated_at=now() WHERE username=? AND password_hash IS NULL", safeName, profile.username());
            return profile(profile.username());
        }

        if (requestedUsername == null)
            throw new PipelineException("GITHUB_INVITATION_REQUIRED", 403,
                "Ask an administrator to create an account before signing in with GitHub");
        Profile profile = profile(requestedUsername);
        if (isGithubLinked(profile.username()))
            throw new PipelineException("GITHUB_ACCOUNT_ALREADY_LINKED", 409, "This RiskGraph account already has a GitHub identity");
        db.update("INSERT INTO external_identities(user_id,provider,provider_subject,provider_login) SELECT id,'github',?,? FROM users WHERE username=?",
            subject, login, profile.username());
        return profile;
    }

    private Profile connectGithubLocally(String subject, String login, String name, String requestedUsername) {
        String existingUsername = localGithubSubjects.get(subject);
        if (existingUsername != null) {
            if (requestedUsername != null && !requestedUsername.equals(existingUsername))
                throw new PipelineException("GITHUB_IDENTITY_IN_USE", 409, "That GitHub identity is already linked to another RiskGraph account");
            localGithubLogins.put(existingUsername, login);
            var existing = local.get(existingUsername);
            if (existing.hash() == null) local.put(existingUsername,
                new Account(new Profile(existingUsername, name, existing.profile().role()), null));
            return local.get(existingUsername).profile();
        }
        if (requestedUsername == null)
            throw new PipelineException("GITHUB_INVITATION_REQUIRED", 403,
                "Ask an administrator to create an account before signing in with GitHub");
        String username = requestedUsername;
        profile(username);
        if (localGithubLogins.containsKey(username))
            throw new PipelineException("GITHUB_ACCOUNT_ALREADY_LINKED", 409, "This RiskGraph account already has a GitHub identity");
        localGithubSubjects.put(subject, username);
        localGithubLogins.put(username, login);
        return local.get(username).profile();
    }

    @Override public UserDetails loadUserByUsername(String username) {
        var account = find(username);
        if (account == null || account.hash() == null) throw new UsernameNotFoundException("Account unavailable");
        return User.withUsername(username).password(account.hash()).roles(account.profile().role().name()).build();
    }
}
