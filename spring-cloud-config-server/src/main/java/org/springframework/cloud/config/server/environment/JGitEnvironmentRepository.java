/*
 * Copyright 2013-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.config.server.environment;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import io.micrometer.observation.ObservationRegistry;
import org.apache.commons.logging.Log;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.StatusCommand;
import org.eclipse.jgit.api.TransportCommand;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.errors.NoRemoteRepositoryException;
import org.eclipse.jgit.lib.BranchTrackingStatus;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.util.FileUtils;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.cloud.config.server.support.GitCredentialsProviderFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.UrlResource;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import static java.lang.String.format;

/**
 * An {@link EnvironmentRepository} backed by a single git repository.
 *
 * @author Dave Syer
 * @author Roy Clarkson
 * @author Marcos Barbero
 * @author Daniel Lavoie
 * @author Ryan Lynch
 * @author Gareth Clay
 * @author ChaoDong Xi
 */
public class JGitEnvironmentRepository extends AbstractScmEnvironmentRepository
		implements EnvironmentRepository, SearchPathLocator, InitializingBean, JGitRepositoryInterfaceMethodeForBranch, JGitRepositoryInterfaceMethodeForSync {

	/**
	 * Error message for URI for git repo.
	 */
	public static final String MESSAGE = "You need to configure a uri for the git repository.";

	private static final String FILE_URI_PREFIX = "file:";


	private final JGitBranchManager JGitBranchManager;
	private final JGitSynchronizer JGitSynchronizer;

	/**
	 * Timeout (in seconds) for obtaining HTTP or SSH connection (if applicable). Default
	 * 5 seconds.
	 */
	private int timeout;



	/**
	 * Flag to indicate that the repository should be cloned on startup (not on demand).
	 * Generally leads to slower startup but faster first query.
	 */
	private boolean cloneOnStart;

	private JGitEnvironmentRepository.JGitFactory gitFactory;

	private String defaultLabel;

	/**
	 * Factory used to create the credentials provider to use to connect to the Git
	 * repository.
	 */
	private GitCredentialsProviderFactory gitCredentialsProviderFactory = new GitCredentialsProviderFactory();

	/**
	 * Transport configuration callback for JGit commands.
	 */
	private TransportConfigCallback transportConfigCallback;


	/**
	 * Flag to indicate that the branch should be deleted locally if it's origin tracked
	 * branch was removed.
	 */
	private boolean deleteUntrackedBranches;

	/**
	 * Flag to indicate that SSL certificate validation should be bypassed when
	 * communicating with a repository served over an HTTPS connection.
	 */
	private boolean skipSslValidation;




	public JGitEnvironmentRepository(ConfigurableEnvironment environment, JGitEnvironmentProperties properties,
			ObservationRegistry observationRegistry) {
		super(environment, properties, observationRegistry);
		this.cloneOnStart = properties.isCloneOnStart();
		this.defaultLabel = properties.getDefaultLabel();
		this.timeout = properties.getTimeout();
		this.deleteUntrackedBranches = properties.isDeleteUntrackedBranches();
		this.skipSslValidation = properties.isSkipSslValidation();
		this.gitFactory = new JGitFactory(properties.isCloneSubmodules());
		this.JGitBranchManager = new JGitBranchManager(this, this.logger, properties.getDefaultLabel(),
				properties.isTryMasterBranch());
		this.JGitSynchronizer = new JGitSynchronizer(this, this.logger,properties.getRefreshRate(),this.getUri(),properties.isForcePull(),this.getWorkingDirectory());

	}

	public boolean isTryMasterBranch() {
		return JGitBranchManager.isTryMasterBranch();
	}

	public void setTryMasterBranch(boolean tryMasterBranch) {
		this.JGitBranchManager.setTryMasterBranch(tryMasterBranch);
	}

	public boolean isCloneOnStart() {
		return this.cloneOnStart;
	}

	public void setCloneOnStart(boolean cloneOnStart) {
		this.cloneOnStart = cloneOnStart;
	}

	public int getTimeout() {
		return this.timeout;
	}

	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	public int getRefreshRate() {
		return this.JGitSynchronizer.getRefreshRate();
	}

	public void setRefreshRate(int refreshRate) {
		this.JGitSynchronizer.setRefreshRate(refreshRate);
	}

	public TransportConfigCallback getTransportConfigCallback() {
		return this.transportConfigCallback;
	}

	public void setTransportConfigCallback(TransportConfigCallback transportConfigCallback) {
		this.transportConfigCallback = transportConfigCallback;
	}

	public JGitFactory getGitFactory() {
		return this.gitFactory;
	}

	public void setGitFactory(JGitFactory gitFactory) {
		this.gitFactory = gitFactory;
	}

	public void setGitCredentialsProviderFactory(GitCredentialsProviderFactory gitCredentialsProviderFactory) {
		this.gitCredentialsProviderFactory = gitCredentialsProviderFactory;
	}

	GitCredentialsProviderFactory getGitCredentialsProviderFactory() {
		return gitCredentialsProviderFactory;
	}

	public String getDefaultLabel() {
		return this.defaultLabel;
	}

	public void setDefaultLabel(String defaultLabel) {
		this.defaultLabel = defaultLabel;
	}


	public void setForcePull(boolean forcePull) {
		this.JGitSynchronizer.setForcePull(forcePull);
	}

	public boolean isDeleteUntrackedBranches() {
		return this.deleteUntrackedBranches;
	}

	public void setDeleteUntrackedBranches(boolean deleteUntrackedBranches) {
		this.deleteUntrackedBranches = deleteUntrackedBranches;
	}

	public boolean isSkipSslValidation() {
		return this.skipSslValidation;
	}

	public void setSkipSslValidation(boolean skipSslValidation) {
		this.skipSslValidation = skipSslValidation;
	}

	@Override
	public synchronized Locations getLocations(String application, String profile, String label) {
		if (label == null) {
			label = this.defaultLabel;
		}
		String version;
		try {
			version = refresh(label);
		}
		catch (Exception e) {
			if (this.defaultLabel.equals(label) && JGitEnvironmentProperties.MAIN_LABEL.equals(this.defaultLabel)
					&& this.isTryMasterBranch()) {
				logger.info("Could not refresh default label " + label, e);
				logger.info("Will try to refresh master label instead.");
				version = refresh(JGitEnvironmentProperties.MASTER_LABEL);
			}
			else {
				throw e;
			}
		}
		return new Locations(application, profile, label, version,
				getSearchLocations(getWorkingDirectory(), application, profile, label));
	}

	@Override
	public synchronized void afterPropertiesSet() throws Exception {
		Assert.state(getUri() != null, MESSAGE);
		if (this.cloneOnStart) {
			initClonedRepository();
		}
	}

	/**
	 * Get the working directory ready.
	 * @param label label to refresh
	 * @return head id
	 */
	public String refresh(String label) {
		Git git = null;
		try {
			git = createGitClient();
			if (JGitSynchronizer.shouldPull(git)) {
				FetchResult fetchStatus = JGitSynchronizer.fetch(git, label);
				if (this.deleteUntrackedBranches && fetchStatus != null) {
					JGitBranchManager.deleteUntrackedLocalBranches(fetchStatus.getTrackingRefUpdates(), git);
				}
			}

			// checkout after fetch so we can get any new branches, tags, ect.
			// if nothing to update so just checkout and merge.
			// Merge because remote branch could have been updated before
			JGitBranchManager.checkout(git, label);
			JGitSynchronizer.tryMerge(git, label);

			// always return what is currently HEAD as the version
			return git.getRepository().findRef("HEAD").getObjectId().getName();
		}
		catch (RefNotFoundException e) {
			throw new NoSuchLabelException("No such label: " + label, e);
		}
		catch (NoRemoteRepositoryException e) {
			throw new NoSuchRepositoryException("No such repository: " + getUri(), e);
		}
		catch (GitAPIException e) {
			throw new NoSuchRepositoryException("Cannot clone or checkout repository: " + getUri(), e);
		}
		catch (Exception e) {
			throw new IllegalStateException("Cannot load environment", e);
		}
		finally {
			try {
				if (git != null) {
					git.close();
				}
			}
			catch (Exception e) {
				this.logger.warn("Could not close git repository", e);
			}
		}
	}



	/**
	 * Clones the remote repository and then opens a connection to it. Checks out to the
	 * defaultLabel if specified.
	 * @throws GitAPIException when cloning fails
	 * @throws IOException when repo opening fails
	 */
	private void initClonedRepository() throws GitAPIException, IOException {
		if (!getUri().startsWith(FILE_URI_PREFIX)) {
			deleteBaseDirIfExists();
			Git git = cloneToBasedir();
			if (git != null) {
				git.close();
			}
			git = openGitRepository();

			// Check if git points to valid repository and default label is not empty or
			// null.
			if (null != git && git.getRepository() != null && !ObjectUtils.isEmpty(getDefaultLabel())) {
				// Checkout the default branch set for repo in git. This may not always be
				// master. It depends on the
				// admin and organization settings.
				String defaultBranchInGit = git.getRepository().getBranch();
				// If default branch is not empty and NOT equal to defaultLabel, then
				// checkout the branch/tag/commit-id.
				if (!ObjectUtils.isEmpty(defaultBranchInGit)
						&& !getDefaultLabel().equalsIgnoreCase(defaultBranchInGit)) {
					JGitBranchManager.checkoutDefaultBranchWithRetry(git);
				}
			}

			if (git != null) {
				git.close();
			}
		}

	}

	protected boolean shouldPull(Git git) throws GitAPIException {

		return JGitSynchronizer.shouldPull(git);
	}


	@SuppressWarnings("unchecked")
	public void logDirty(Status status) {
		Set<String> dirties = dirties(status.getAdded(), status.getChanged(), status.getRemoved(), status.getMissing(),
				status.getModified(), status.getConflicting(), status.getUntracked());
		this.logger.warn(format("Dirty files found: %s", dirties));
	}

	@SuppressWarnings("unchecked")
	private Set<String> dirties(Set<String>... changes) {
		Set<String> dirties = new HashSet<>();
		for (Set<String> files : changes) {
			dirties.addAll(files);
		}
		return dirties;
	}


	protected FetchResult fetch(Git git, String label) {

		return JGitSynchronizer.fetch(git, label);
	}


	private Git createGitClient() throws IOException, GitAPIException {
		File lock = new File(getWorkingDirectory(), ".git/index.lock");
		if (lock.exists()) {
			// The only way this can happen is if another JVM (e.g. one that
			// crashed earlier) created the lock. We can attempt to recover by
			// wiping the slate clean.
			this.logger.info("Deleting stale JGit lock file at " + lock);
			lock.delete();
		}
		if (new File(getWorkingDirectory(), ".git").exists()) {
			return openGitRepository();
		}
		else {
			return copyRepository();
		}
	}

	// Synchronize here so that multiple requests don't all try and delete the
	// base dir
	// together (this is a once only operation, so it only holds things up on
	// the first
	// request).
	private synchronized Git copyRepository() throws IOException, GitAPIException {
		deleteBaseDirIfExists();
		getBasedir().mkdirs();
		Assert.state(getBasedir().exists(), "Could not create basedir: " + getBasedir());
		if (getUri().startsWith(FILE_URI_PREFIX)) {
			return copyFromLocalRepository();
		}
		else {
			return cloneToBasedir();
		}
	}

	private Git openGitRepository() throws IOException {
		Git git = this.gitFactory.getGitByOpen(getWorkingDirectory());
		return git;
	}

	private Git copyFromLocalRepository() throws IOException {
		Git git;
		File remote = new UrlResource(StringUtils.cleanPath(getUri())).getFile();
		Assert.state(remote.isDirectory(), "No directory at " + getUri());
		File gitDir = new File(remote, ".git");
		Assert.state(gitDir.exists(), "No .git at " + getUri());
		Assert.state(gitDir.isDirectory(), "No .git directory at " + getUri());
		git = this.gitFactory.getGitByOpen(remote);
		return git;
	}

	private Git cloneToBasedir() throws GitAPIException {
		CloneCommand clone = this.gitFactory.getCloneCommandByCloneRepository()
			.setURI(getUri())
			.setDirectory(getBasedir());
		configureCommand(clone);
		try {
			return clone.call();
		}
		catch (GitAPIException e) {
			this.logger.warn("Error occured cloning to base directory.", e);
			deleteBaseDirIfExists();
			throw e;
		}
	}

	private void deleteBaseDirIfExists() {
		if (getBasedir().exists()) {
			for (File file : getBasedir().listFiles()) {
				try {
					FileUtils.delete(file, FileUtils.RECURSIVE);
				}
				catch (IOException e) {
					throw new IllegalStateException("Failed to initialize base directory", e);
				}
			}
		}
	}

	public void configureCommand(TransportCommand<?, ?> command) {
		command.setTimeout(this.timeout);
		if (this.transportConfigCallback != null) {
			command.setTransportConfigCallback(this.transportConfigCallback);
		}
		CredentialsProvider credentialsProvider = getCredentialsProvider();
		if (credentialsProvider != null) {
			command.setCredentialsProvider(credentialsProvider);
		}
	}

	private CredentialsProvider getCredentialsProvider() {
		return this.gitCredentialsProviderFactory.createFor(this.getUri(), getUsername(), getPassword(),
				getPassphrase(), isSkipSslValidation());
	}

	public boolean isClean(Git git, String label) {
		StatusCommand status = git.status();
		try {
			BranchTrackingStatus trackingStatus = BranchTrackingStatus.of(git.getRepository(), label);
			boolean isBranchAhead = trackingStatus != null && trackingStatus.getAheadCount() > 0;
			return status.call().isClean() && !isBranchAhead;
		}
		catch (Exception e) {
			String message = "Could not execute status command on local repository. Cause: ("
					+ e.getClass().getSimpleName() + ") " + e.getMessage();
			warn(message, e);
			return false;
		}
	}

	public void warn(String message, Exception ex) {
		this.logger.warn(message);
		if (this.logger.isDebugEnabled()) {
			this.logger.debug("Stacktrace for: " + message, ex);
		}
	}

	@Override
	public boolean isBranch(Git git, String label) throws GitAPIException {
		return this.JGitBranchManager.isBranch(git,label);
	}

	public long getLastRefresh() {
		return this.JGitSynchronizer.getLastRefresh();
	}

	public void setLastRefresh(long lastRefresh) {
		this.JGitSynchronizer.setLastRefresh(lastRefresh);
	}

	public Log getLogger() {
		return logger;
	}


	/**
	 * Wraps the static method calls to {@link org.eclipse.jgit.api.Git} and
	 * {@link org.eclipse.jgit.api.CloneCommand} allowing for easier unit testing.
	 */
	public static class JGitFactory {

		private final boolean cloneSubmodules;

		public JGitFactory() {
			this(false);
		}

		public JGitFactory(boolean cloneSubmodules) {
			this.cloneSubmodules = cloneSubmodules;
		}

		public Git getGitByOpen(File file) throws IOException {
			Git git = Git.open(file);
			return git;
		}

		public CloneCommand getCloneCommandByCloneRepository() {
			CloneCommand command = Git.cloneRepository().setCloneSubmodules(cloneSubmodules);
			return command;
		}

	}

}
