package io.github.sonofmagic.javachanges.core.publish;

import io.github.sonofmagic.javachanges.core.CommandResult;
import io.github.sonofmagic.javachanges.core.ReleaseProcessUtils;
import io.github.sonofmagic.javachanges.core.ReleaseTextUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PublishRuntime {
    private static final DateTimeFormatter SNAPSHOT_TIMESTAMP =
        DateTimeFormatter.ofPattern("yyyyMMdd.HHmmss").withZone(ZoneOffset.UTC);
    private final Path repoRoot;

    public PublishRuntime(Path repoRoot) {
        this.repoRoot = repoRoot;
    }

    public boolean hasDirtyWorktree() throws IOException, InterruptedException {
        CommandResult result = worktreeStatus();
        if (result.exitCode != 0) {
            String error = result.stderrText().trim();
            throw new IllegalStateException(error.isEmpty() ? "Git status failed." : error);
        }
        return !result.stdoutText().trim().isEmpty();
    }

    public CommandResult worktreeStatus() throws IOException, InterruptedException {
        List<String> command = new ArrayList<String>();
        command.add("git");
        command.add("status");
        command.add("--short");

        String cachePathspec = localMavenRepositoryExcludePathspec();
        if (cachePathspec != null) {
            command.add("--");
            command.add(".");
            command.add(cachePathspec);
        }
        return ReleaseProcessUtils.runCapture(repoRoot, command);
    }

    private String localMavenRepositoryExcludePathspec() throws IOException, InterruptedException {
        CommandResult rootResult = ReleaseProcessUtils.runCapture(repoRoot, "git", "rev-parse", "--show-toplevel");
        if (rootResult.exitCode != 0) {
            return null;
        }

        Path worktreeRoot = Paths.get(rootResult.stdoutText().trim()).toAbsolutePath().normalize();
        Path lexicalRepoRoot = repoRoot.toAbsolutePath().normalize();
        Path localRepoPath = localMavenRepositoryPath().toAbsolutePath().normalize();
        if (localRepoPath.startsWith(lexicalRepoRoot)) {
            localRepoPath = worktreeRoot.resolve(lexicalRepoRoot.relativize(localRepoPath)).normalize();
        } else if (Files.exists(localRepoPath)) {
            localRepoPath = localRepoPath.toRealPath().normalize();
        }
        if (!localRepoPath.startsWith(worktreeRoot) || localRepoPath.equals(worktreeRoot)) {
            return null;
        }

        String relativePath = worktreeRoot.relativize(localRepoPath).toString().replace(File.separatorChar, '/');
        String literalPathspec = ":(top,literal)" + relativePath;
        CommandResult trackedFiles = ReleaseProcessUtils.runCapture(repoRoot,
            "git", "ls-files", "--", literalPathspec);
        if (!trackedFiles.stdoutText().trim().isEmpty()) {
            return null;
        }
        return ":(top,literal,exclude)" + relativePath;
    }

    public boolean gitRefExists(String ref) throws IOException, InterruptedException {
        return ReleaseProcessUtils.runCapture(repoRoot, "git", "rev-parse", ref).exitCode == 0;
    }

    public String snapshotBuildStamp() throws IOException, InterruptedException {
        String timestamp = SNAPSHOT_TIMESTAMP.format(Instant.now());
        String shortSha = gitHeadShortSha();
        return timestamp + "." + shortSha;
    }

    public Path localMavenRepositoryPath() {
        Path defaultLocalRepo = repoRoot.resolve(".m2/repository").normalize();

        String mavenOpts = ReleaseTextUtils.trimToNull(System.getenv("MAVEN_OPTS"));
        if (mavenOpts == null) {
            return defaultLocalRepo;
        }

        Matcher matcher = Pattern.compile("(?:^|\\s)-Dmaven\\.repo\\.local=(\"[^\"]*\"|'[^']*'|[^\\s]+)")
            .matcher(mavenOpts);
        if (!matcher.find()) {
            return defaultLocalRepo;
        }

        String configuredPath = matcher.group(1);
        if ((configuredPath.startsWith("\"") && configuredPath.endsWith("\""))
            || (configuredPath.startsWith("'") && configuredPath.endsWith("'"))) {
            configuredPath = configuredPath.substring(1, configuredPath.length() - 1);
        }

        Path localRepoPath = Paths.get(configuredPath);
        if (!localRepoPath.isAbsolute()) {
            localRepoPath = repoRoot.resolve(localRepoPath).normalize();
        }
        return localRepoPath;
    }

    public Path ensureLocalMavenRepositoryDirectory() throws IOException {
        Path localRepoPath = localMavenRepositoryPath();
        Files.createDirectories(localRepoPath);
        return localRepoPath;
    }

    private String gitHeadShortSha() throws IOException, InterruptedException {
        CommandResult result = ReleaseProcessUtils.runCapture(repoRoot, "git", "rev-parse", "--short", "HEAD");
        if (result.exitCode != 0) {
            return "nogit";
        }
        String value = ReleaseTextUtils.trimToNull(result.stdoutText());
        return value == null ? "nogit" : value;
    }
}
