package ai.riskgraph.analyzer.source;

import static ai.riskgraph.analyzer.model.AnalysisModels.ChangedFile;
import static ai.riskgraph.analyzer.model.AnalysisModels.ChangedRange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class GitSourceAcquirer {
    private static final Logger LOG = LoggerFactory.getLogger(GitSourceAcquirer.class);
    private static final Pattern FULL_SHA = Pattern.compile("[0-9a-fA-F]{40}");
    private final List<Path> allowedRoots;
    private final int maxJavaFiles;
    private final long maxJavaFileBytes;
    private final long maxTotalJavaBytes;
    private final SnapshotCache snapshotCache;

    @Autowired
    public GitSourceAcquirer(
            @Value("${riskgraph.analyzer.allowed-repository-roots}") String roots,
            @Value("${riskgraph.analyzer.max-java-files}") int maxJavaFiles,
            @Value("${riskgraph.analyzer.max-java-file-bytes}") long maxJavaFileBytes,
            @Value("${riskgraph.analyzer.max-total-java-bytes}") long maxTotalJavaBytes,
            SnapshotCache snapshotCache
    ) {
        this.allowedRoots = Pattern.compile(Pattern.quote(System.getProperty("path.separator")))
                .splitAsStream(roots)
                .filter(value -> !value.isBlank())
                .map(value -> Path.of(value).toAbsolutePath().normalize())
                .toList();
        if (this.allowedRoots.isEmpty()) {
            throw new IllegalStateException(
                    "RISKGRAPH_ALLOWED_REPOSITORY_ROOTS must be configured");
        }
        this.maxJavaFiles = maxJavaFiles;
        this.maxJavaFileBytes = maxJavaFileBytes;
        this.maxTotalJavaBytes = maxTotalJavaBytes;
        this.snapshotCache = snapshotCache;
    }

    public GitSourceAcquirer(String roots) {
        this(roots, 5000, 2L * 1024 * 1024, 50L * 1024 * 1024,
                new SnapshotCache(Path.of(System.getProperty("java.io.tmpdir"),
                        "riskgraph-test-snapshot-cache"), 8, 100L * 1024 * 1024,
                        java.time.Duration.ofHours(1), java.time.Clock.systemUTC()));
    }

    GitSourceAcquirer(String roots, int maxJavaFiles, long maxJavaFileBytes,
            long maxTotalJavaBytes) {
        this(roots, maxJavaFiles, maxJavaFileBytes, maxTotalJavaBytes,
                new SnapshotCache(Path.of(System.getProperty("java.io.tmpdir"),
                        "riskgraph-test-snapshot-cache"), 8, 100L * 1024 * 1024,
                        java.time.Duration.ofHours(1), java.time.Clock.systemUTC()));
    }

    public AcquiredRevisions acquire(Path requestedPath, String oldSha, String newSha) {
        Path repositoryPath = validatePath(requestedPath);
        validateSha(oldSha);
        validateSha(newSha);

        try {
            Repository repository = new FileRepositoryBuilder().findGitDir(repositoryPath.toFile()).build();
            if (repository.getDirectory() == null) {
                repository.close();
                throw new SourceAcquisitionException("NOT_A_GIT_REPOSITORY", "Path is not a Git repository");
            }
            if (repository.isBare() || !repository.getWorkTree().toPath().toRealPath().equals(repositoryPath)) {
                repository.close();
                throw new SourceAcquisitionException("NOT_A_GIT_REPOSITORY", "Path must be the Git repository root");
            }
            RevCommit oldCommit;
            RevCommit newCommit;
            try {
                oldCommit = parseExactCommit(repository, oldSha);
                newCommit = parseExactCommit(repository, newSha);
            } catch (SourceAcquisitionException error) {
                repository.close();
                throw error;
            }
            SnapshotCache.Lease oldSnapshot = null;
            SnapshotCache.Lease newSnapshot = null;
            try {
                String identity = repository.getConfig().getString("remote", "origin", "url");
                if (identity == null || identity.isBlank()) {
                    identity = localRepositoryIdentity(repositoryPath);
                } else if (identity.contains("://")) {
                    try {
                        var uri = java.net.URI.create(identity);
                        identity = new java.net.URI(uri.getScheme(), null, uri.getHost(), uri.getPort(),
                            uri.getPath(), null, null).toString();
                    } catch (Exception error) {
                        identity = localRepositoryIdentity(repositoryPath);
                    }
                }
                String limits = maxJavaFiles + ":" + maxJavaFileBytes + ":" + maxTotalJavaBytes;
                String cacheIdentity = identity;
                oldSnapshot = snapshotCache.acquire(cacheIdentity, oldCommit.getName(), limits,
                        destination -> materialize(repository, oldCommit, destination));
                newSnapshot = snapshotCache.acquire(cacheIdentity, newCommit.getName(), limits,
                        destination -> materialize(repository, newCommit, destination));
                // Enforce blob limits during cache population before diff processing reads content.
                List<ChangedFile> changedFiles = diff(repository, oldCommit, newCommit);
                return new AcquiredRevisions(repository, repositoryPath, identity,
                        oldCommit.getName(), newCommit.getName(), oldSnapshot.path(), newSnapshot.path(),
                        changedFiles, oldSnapshot.cached(), newSnapshot.cached(), snapshotCache);
            } catch (RuntimeException | IOException error) {
                if (oldSnapshot != null) {
                    if (oldSnapshot.cached()) snapshotCache.release(oldSnapshot.path());
                    else deleteTree(oldSnapshot.path());
                }
                if (newSnapshot != null) {
                    if (newSnapshot.cached()) snapshotCache.release(newSnapshot.path());
                    else deleteTree(newSnapshot.path());
                }
                repository.close();
                throw error;
            }
        } catch (SourceAcquisitionException error) {
            throw error;
        } catch (IOException error) {
            throw new SourceAcquisitionException("SOURCE_ACQUISITION_FAILED", error.getMessage());
        }
    }

    /** Stable identity for a repository path, without materializing a snapshot. */
    public String repositoryIdentity(Path repositoryPath) {
        return localRepositoryIdentity(validatePath(repositoryPath));
    }

    private String localRepositoryIdentity(Path repositoryPath) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    repositoryPath.toUri().normalize().toASCIIString().getBytes(StandardCharsets.UTF_8));
            return "local-git:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private Path validatePath(Path requestedPath) {
        try {
            Path realPath = requestedPath.toRealPath();
            boolean allowed = allowedRoots.stream().anyMatch(realPath::startsWith);
            if (!allowed) {
                throw new SourceAcquisitionException("REPOSITORY_NOT_ALLOWED",
                        "Repository must be inside an allowed repository root");
            }
            return realPath;
        } catch (IOException error) {
            throw new SourceAcquisitionException("REPOSITORY_NOT_FOUND", "Repository path does not exist");
        }
    }

    private void validateSha(String sha) {
        if (!FULL_SHA.matcher(sha).matches()) {
            throw new SourceAcquisitionException("INVALID_COMMIT", "Commit must be a full 40-character SHA");
        }
    }

    private RevCommit parseExactCommit(Repository repository, String sha) {
        try {
            ObjectId objectId = repository.resolve(sha + "^{commit}");
            if (objectId == null || !objectId.name().equalsIgnoreCase(sha)) {
                throw new SourceAcquisitionException(
                        "INVALID_COMMIT", "Commit does not exist in the repository: " + sha);
            }
            try (RevWalk walk = new RevWalk(repository)) {
                return walk.parseCommit(objectId);
            }
        } catch (SourceAcquisitionException error) {
            throw error;
        } catch (IOException error) {
            throw new SourceAcquisitionException(
                    "INVALID_COMMIT", "Commit does not exist in the repository: " + sha);
        }
    }

    private List<ChangedFile> diff(Repository repository, RevCommit oldCommit, RevCommit newCommit)
            throws IOException {
        List<ChangedFile> result = new ArrayList<>();
        try (DiffFormatter formatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            formatter.setRepository(repository);
            formatter.setDetectRenames(true);
            for (DiffEntry entry : formatter.scan(oldCommit.getTree(), newCommit.getTree())) {
                checkInterrupted();
                String oldPath = normalizeDiffPath(entry.getOldPath());
                String newPath = normalizeDiffPath(entry.getNewPath());
                boolean javaChange = isJava(oldPath) || isJava(newPath);
                if (!javaChange) {
                    continue;
                }
                List<ChangedRange> oldRanges = new ArrayList<>();
                List<ChangedRange> newRanges = new ArrayList<>();
                for (Edit edit : formatter.toFileHeader(entry).toEditList()) {
                    addRange(oldRanges, edit.getBeginA(), edit.getEndA());
                    addRange(newRanges, edit.getBeginB(), edit.getEndB());
                }
                result.add(new ChangedFile(entry.getChangeType().name(), oldPath, newPath,
                        List.copyOf(oldRanges), List.copyOf(newRanges)));
            }
        }
        return result.stream()
                .sorted(Comparator.comparing(file -> file.new_path() == null ? file.old_path() : file.new_path()))
                .toList();
    }

    private void addRange(List<ChangedRange> ranges, int zeroBasedStart, int zeroBasedEnd) {
        int startLine = zeroBasedStart + 1;
        int endLine = zeroBasedStart == zeroBasedEnd ? startLine : zeroBasedEnd;
        ranges.add(new ChangedRange(startLine, endLine));
    }

    private String normalizeDiffPath(String path) {
        return DiffEntry.DEV_NULL.equals(path) ? null : path.replace('\\', '/');
    }

    private boolean isJava(String path) {
        return path != null && path.toLowerCase(Locale.ROOT).endsWith(".java");
    }

    private void materialize(Repository repository, RevCommit commit, Path destination) throws IOException {
        int javaFileCount = 0;
        long totalBytes = 0;
        try (TreeWalk tree = new TreeWalk(repository)) {
            tree.addTree(commit.getTree());
            tree.setRecursive(true);
            while (tree.next()) {
                checkInterrupted();
                if (!isJava(tree.getPathString())) {
                    continue;
                }
                if (!FileMode.REGULAR_FILE.equals(tree.getFileMode(0))
                        && !FileMode.EXECUTABLE_FILE.equals(tree.getFileMode(0))) {
                    throw new SourceAcquisitionException(
                            "UNSUPPORTED_JAVA_ENTRY", "Java source must be a regular Git blob: " + tree.getPathString());
                }
                javaFileCount++;
                if (javaFileCount > maxJavaFiles) {
                    throw new SourceAcquisitionException("SOURCE_LIMIT_EXCEEDED", "Java file count exceeds configured limit");
                }
                Path output = destination.resolve(tree.getPathString()).normalize();
                if (!output.startsWith(destination)) {
                    throw new SourceAcquisitionException("UNSAFE_REPOSITORY_PATH", "Unsafe path in Git tree");
                }
                Files.createDirectories(output.getParent());
                ObjectLoader loader = repository.open(tree.getObjectId(0), Constants.OBJ_BLOB);
                long blobSize = loader.getSize();
                if (blobSize > maxJavaFileBytes || totalBytes + blobSize > maxTotalJavaBytes) {
                    throw new SourceAcquisitionException(
                            "SOURCE_LIMIT_EXCEEDED", "Java source size exceeds configured limit: " + tree.getPathString());
                }
                byte[] contents = loader.getBytes((int) maxJavaFileBytes);
                if (isLfsPointer(contents)) {
                    throw new SourceAcquisitionException(
                            "GIT_LFS_POINTER", "Git LFS Java source is not materialized: " + tree.getPathString());
                }
                Files.write(output, contents);
                totalBytes += blobSize;
            }
        }
    }

    private boolean isLfsPointer(byte[] contents) {
        String prefix = "version https://git-lfs.github.com/spec/v1";
        if (contents.length < prefix.length()) {
            return false;
        }
        return new String(contents, 0, prefix.length(), java.nio.charset.StandardCharsets.US_ASCII)
                .equals(prefix);
    }

    private static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new SourceAcquisitionException("ANALYSIS_INTERRUPTED", "Source acquisition was interrupted");
        }
    }

    private static void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException error) {
                    LOG.warn("snapshot_cleanup_failed artifact={} error_type={}",
                            path.getFileName(), error.getClass().getSimpleName());
                }
            });
        } catch (IOException error) {
            LOG.warn("snapshot_cleanup_walk_failed artifact={} error_type={}",
                    root.getFileName(), error.getClass().getSimpleName());
        }
    }

    public record AcquiredRevisions(
            Repository repository,
            Path repositoryPath,
            String repositoryIdentity,
            String oldCommit,
            String newCommit,
            Path oldSnapshot,
            Path newSnapshot,
            List<ChangedFile> changedFiles,
            boolean oldSnapshotCached,
            boolean newSnapshotCached,
            SnapshotCache snapshotCache
    ) implements AutoCloseable {
        @Override
        public void close() {
            if (oldSnapshotCached) snapshotCache.release(oldSnapshot);
            else deleteTree(oldSnapshot);
            if (newSnapshotCached) snapshotCache.release(newSnapshot);
            else deleteTree(newSnapshot);
            repository.close();
        }
    }
}
