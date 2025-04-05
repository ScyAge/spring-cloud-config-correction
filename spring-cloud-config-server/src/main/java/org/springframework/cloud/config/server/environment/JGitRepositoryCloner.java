package org.springframework.cloud.config.server.environment;

import org.apache.commons.logging.Log;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.util.FileUtils;
import org.springframework.core.io.UrlResource;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.cloud.config.server.environment.JGitEnvironmentRepository.JGitFactory;

import java.io.File;
import java.io.IOException;

public class JGitRepositoryCloner {
	private final JGitRepositoryInterfaceMethodeForClone JGitEnvironmentRepository;
	private static final String FILE_URI_PREFIX = "file:";
	private final JGitFactory gitFactory;
	private File baseDir;

	private final Log logger;
	private File workingDirectory;
	private String defaultLabel;

	public JGitRepositoryCloner(JGitRepositoryInterfaceMethodeForClone JGitEnvironmentRepository, File baseDir, Log logger,
								File workingDirectory, String defaultLabel, JGitFactory gitFactory) {
		this.JGitEnvironmentRepository = JGitEnvironmentRepository;
		this.baseDir = baseDir;
		this.logger = logger;
		this.workingDirectory = workingDirectory;
		this.defaultLabel = defaultLabel;
		this.gitFactory = gitFactory;
	}

	/**
	 * Clones the remote repository and then opens a connection to it. Checks out to the
	 * defaultLabel if specified.
	 *
	 * @throws GitAPIException when cloning fails
	 * @throws IOException     when repo opening fails
	 */
	void initClonedRepository() throws GitAPIException, IOException {
		if (!JGitEnvironmentRepository.getUri().startsWith(FILE_URI_PREFIX)) {
			this.deleteBaseDirIfExists();
			Git git = cloneToBasedir();
			if (git != null) {
				git.close();
			}
			git = openGitRepository();

			// Check if git points to valid repository and default label is not empty or
			// null.
			if (null != git && git.getRepository() != null && !ObjectUtils.isEmpty(this.getDefaultLabel())) {
				// Checkout the default branch set for repo in git. This may not always be
				// master. It depends on the
				// admin and organization settings.
				String defaultBranchInGit = git.getRepository().getBranch();
				// If default branch is not empty and NOT equal to defaultLabel, then
				// checkout the branch/tag/commit-id.
				if (!ObjectUtils.isEmpty(defaultBranchInGit)
					&& !this.getDefaultLabel().equalsIgnoreCase(defaultBranchInGit)) {
					JGitEnvironmentRepository.checkoutDefaultBranchWithRetry(git);
				}
			}

			if (git != null) {
				git.close();
			}
		}

	}

	// Synchronize here so that multiple requests don't all try and delete the

	// base dir
	// together (this is a once only operation, so it only holds things up on
	// the first
	// request).
	synchronized Git copyRepository() throws IOException, GitAPIException {
		this.deleteBaseDirIfExists();
		this.getBasedir().mkdirs();
		Assert.state(this.getBasedir().exists(), "Could not create basedir: " + this.getBasedir());
		if (JGitEnvironmentRepository.getUri().startsWith(FILE_URI_PREFIX)) {
			return copyFromLocalRepository();
		} else {
			return cloneToBasedir();
		}
	}

	Git openGitRepository() throws IOException {
		Git git = this.getGitFactory().getGitByOpen(this.getWorkingDirectory());
		return git;
	}

	Git copyFromLocalRepository() throws IOException {
		Git git;
		File remote = new UrlResource(StringUtils.cleanPath(JGitEnvironmentRepository.getUri())).getFile();
		Assert.state(remote.isDirectory(), "No directory at " + JGitEnvironmentRepository.getUri());
		File gitDir = new File(remote, ".git");
		Assert.state(gitDir.exists(), "No .git at " + JGitEnvironmentRepository.getUri());
		Assert.state(gitDir.isDirectory(), "No .git directory at " + JGitEnvironmentRepository.getUri());
		git = this.getGitFactory().getGitByOpen(remote);
		return git;
	}

	Git cloneToBasedir() throws GitAPIException {
		CloneCommand clone = this.getGitFactory().getCloneCommandByCloneRepository()
			.setURI(JGitEnvironmentRepository.getUri())
			.setDirectory(this.getBasedir());
		JGitEnvironmentRepository.configureCommand(clone);
		try {
			return clone.call();
		} catch (GitAPIException e) {
			this.getLogger().warn("Error occured cloning to base directory.", e);
			this.deleteBaseDirIfExists();
			throw e;
		}
	}


	private void deleteBaseDirIfExists() {
		if (this.getBasedir().exists()) {
			for (File file : this.getBasedir().listFiles()) {
				try {
					FileUtils.delete(file, FileUtils.RECURSIVE);
				}
				catch (IOException e) {
					throw new IllegalStateException("Failed to initialize base directory", e);
				}
			}
		}
	}

	public File getBasedir() {
		return baseDir;
	}

	public Log getLogger() {
		return logger;
	}

	public File getWorkingDirectory() {
		return workingDirectory;
	}

	public String getDefaultLabel() {
		return defaultLabel;
	}

	public JGitFactory getGitFactory() {
		return gitFactory;
	}
}
