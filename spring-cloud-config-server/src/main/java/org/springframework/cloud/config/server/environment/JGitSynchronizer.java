package org.springframework.cloud.config.server.environment;

import org.apache.commons.logging.Log;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.TagOpt;

import java.io.File;

public class JGitSynchronizer {
	private final JGitRepositoryInterfaceMethodeForSync JGitEnvironmentRepository;

	private final Log logger;
	/**
	 * Time (in seconds) between refresh of the git repository.
	 */
	private int refreshRate = 0;

	/**
	 * Time of the last refresh of the git repository.
	 */
	private long lastRefresh;



	private File workingDirectory;

	/**
	 * Flag to indicate that the repository should force pull. If true discard any local
	 * changes and take from remote repository.
	 */
	private boolean forcePull;

	private static final String LOCAL_BRANCH_REF_PREFIX = "refs/remotes/origin/";

	public JGitSynchronizer(JGitRepositoryInterfaceMethodeForSync JGitEnvironmentRepository, Log logger, int refreshRate,  boolean forcePull, File workingDirectory) {
		this.JGitEnvironmentRepository = JGitEnvironmentRepository;
		this.logger = logger;
		this.refreshRate = refreshRate;
		this.forcePull = forcePull;
		this.workingDirectory = workingDirectory;
	}

	void tryMerge(Git git, String label) {
		try {
			if (JGitEnvironmentRepository.isBranch(git, label)) {
				// merge results from fetch
				merge(git, label);
				if (!JGitEnvironmentRepository.isClean(git, label)) {
					this.getLogger().warn("The local repository is dirty or ahead of origin. Resetting" + " it to origin/"
						+ label + ".");
					resetHard(git, label, LOCAL_BRANCH_REF_PREFIX + label);
				}
			}
		} catch (GitAPIException e) {
			throw new NoSuchRepositoryException("Cannot clone or checkout repository: " + JGitEnvironmentRepository.getUri(), e);
		}
	}

	protected boolean shouldPull(Git git) throws GitAPIException {
		boolean shouldPull;

		if (this.refreshRate < 0 || (this.refreshRate > 0
			&& System.currentTimeMillis() - this.getLastRefresh() < (this.refreshRate * 1000))) {
			return false;
		}

		Status gitStatus;
		try {
			gitStatus = git.status().call();
		} catch (JGitInternalException e) {
			this.onPullInvalidIndex(git, e);
			gitStatus = git.status().call();
		}

		boolean isWorkingTreeClean = gitStatus.isClean();
		String originUrl = git.getRepository().getConfig().getString("remote", "origin", "url");

		if (this.isForcePull() && !isWorkingTreeClean) {
			shouldPull = true;
			JGitEnvironmentRepository.logDirty(gitStatus);
		} else {
			shouldPull = isWorkingTreeClean && originUrl != null;
		}
		if (!isWorkingTreeClean && !this.isForcePull()) {
			this.getLogger().info("Cannot pull from remote " + originUrl + ", the working tree is not clean.");
		}
		return shouldPull;
	}

	protected FetchResult fetch(Git git, String label) {
		FetchCommand fetch = git.fetch();
		fetch.setRemote("origin");
		fetch.setTagOpt(TagOpt.FETCH_TAGS);
		fetch.setRemoveDeletedRefs(JGitEnvironmentRepository.isDeleteUntrackedBranches());
		if (this.refreshRate > 0) {
			this.setLastRefresh(System.currentTimeMillis());
		}

		JGitEnvironmentRepository.configureCommand(fetch);
		try {
			FetchResult result = fetch.call();
			if (result.getTrackingRefUpdates() != null && result.getTrackingRefUpdates().size() > 0) {
				this.getLogger().info("Fetched for remote " + label + " and found " + result.getTrackingRefUpdates().size()
					+ " updates");
			}
			return result;
		} catch (Exception ex) {
			String message = "Could not fetch remote for " + label + " remote: "
				+ git.getRepository().getConfig().getString("remote", "origin", "url");
			JGitEnvironmentRepository.warn(message, ex);
			return null;
		}
	}

	MergeResult merge(Git git, String label) {
		try {
			MergeCommand merge = git.merge();
			merge.include(git.getRepository().findRef("origin/" + label));
			MergeResult result = merge.call();
			if (!result.getMergeStatus().isSuccessful()) {
				this.getLogger().warn("Merged from remote " + label + " with result " + result.getMergeStatus());
			}
			return result;
		} catch (Exception ex) {
			String message = "Could not merge remote for " + label + " remote: "
				+ git.getRepository().getConfig().getString("remote", "origin", "url");
			JGitEnvironmentRepository.warn(message, ex);
			return null;
		}
	}

	Ref resetHard(Git git, String label, String ref) {
		ResetCommand reset = git.reset();
		reset.setRef(ref);
		reset.setMode(ResetCommand.ResetType.HARD);
		try {
			Ref resetRef = reset.call();
			if (resetRef != null) {
				this.getLogger().info("Reset label " + label + " to version " + resetRef.getObjectId());
			}
			return resetRef;
		} catch (Exception ex) {
			String message = "Could not reset to remote for " + label + " (current ref=" + ref + "), remote: "
				+ git.getRepository().getConfig().getString("remote", "origin", "url");
			JGitEnvironmentRepository.warn(message, ex);
			return null;
		}
	}

	public void onPullInvalidIndex(Git git, JGitInternalException e) {
		if (!e.getMessage().contains("Short read of block.")) {
			throw e;
		}
		if (!this.isForcePull()) {
			throw e;
		}
		try {
			new File(this.getWorkingDirectory(), ".git/index").delete();
			git.reset().setMode(ResetCommand.ResetType.HARD).setRef("HEAD").call();
		}
		catch (GitAPIException ex) {
			e.addSuppressed(ex);
			throw e;
		}
	}

	public Log getLogger() {
		return logger;
	}

	public int getRefreshRate() {
		return this.refreshRate;
	}

	public void setRefreshRate(int refreshRate) {
		this.refreshRate = refreshRate;
	}

	public long getLastRefresh() {
		return this.lastRefresh;
	}

	public void setLastRefresh(long lastRefresh) {
		this.lastRefresh = lastRefresh;
	}

	public boolean isForcePull() {
		return forcePull;
	}

	public void setForcePull(boolean forcePull) {
		this.forcePull = forcePull;
	}

	public File getWorkingDirectory() {
		return workingDirectory;
	}
}
