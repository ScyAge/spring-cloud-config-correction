package org.springframework.cloud.config.server.environment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.commons.logging.Log;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.TrackingRefUpdate;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.DeleteBranchCommand;

import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import static org.eclipse.jgit.transport.ReceiveCommand.Type.DELETE;

public class JGitBranchManager {

	private final JGitRepositoryInterfaceMethodeForBranch JGitEnvironmentRepository;

	private static final String LOCAL_BRANCH_REF_PREFIX = "refs/remotes/origin/";

	private boolean isTryMasterBranch;

	private Log logger;

	private String defaultLabel;

	public JGitBranchManager(JGitRepositoryInterfaceMethodeForBranch JGitEnvironmentRepository, Log logger, String defaultLabel,
							 boolean isTryMasterBranch) {
		this.JGitEnvironmentRepository = JGitEnvironmentRepository;
		this.logger = logger;
		this.defaultLabel = defaultLabel;
		this.isTryMasterBranch = isTryMasterBranch;
	}

	void checkoutDefaultBranchWithRetry(Git git) throws GitAPIException {
		try {
			checkout(git, this.getDefaultLabel());
		}
		catch (Exception e) {
			if (JGitEnvironmentProperties.MAIN_LABEL.equals(this.getDefaultLabel()) && this.isTryMasterBranch()) {
				this.logger.info("Could not checkout default label " + this.getDefaultLabel(), e);
				this.logger.info("Will try to checkout master label instead.");
				checkout(git, JGitEnvironmentProperties.MASTER_LABEL);
			}
			else {
				throw e;
			}
		}

	}

	/**
	 * Deletes local branches if corresponding remote branch was removed.
	 * @param trackingRefUpdates list of tracking ref updates
	 * @param git git instance
	 * @return list of deleted branches
	 */
	Collection<String> deleteUntrackedLocalBranches(Collection<TrackingRefUpdate> trackingRefUpdates, Git git) {
		if (CollectionUtils.isEmpty(trackingRefUpdates)) {
			return Collections.emptyList();
		}

		Collection<String> branchesToDelete = new ArrayList<String>();
		for (TrackingRefUpdate trackingRefUpdate : trackingRefUpdates) {
			ReceiveCommand receiveCommand = trackingRefUpdate.asReceiveCommand();
			if (receiveCommand.getType() == DELETE) {
				String localRefName = trackingRefUpdate.getLocalName();
				if (StringUtils.startsWithIgnoreCase(localRefName, LOCAL_BRANCH_REF_PREFIX)) {
					String localBranchName = localRefName.substring(LOCAL_BRANCH_REF_PREFIX.length(),
							localRefName.length());
					branchesToDelete.add(localBranchName);
				}
			}
		}

		if (CollectionUtils.isEmpty(branchesToDelete)) {
			return Collections.emptyList();
		}

		try {
			// make sure that deleted branch not a current one
			checkoutDefaultBranchWithRetry(git);
			return deleteBranches(git, branchesToDelete);
		}
		catch (Exception ex) {
			String message = String.format("Failed to delete %s branches.", branchesToDelete);
			JGitEnvironmentRepository.warn(message, ex);
			return Collections.emptyList();
		}
	}

	List<String> deleteBranches(Git git, Collection<String> branchesToDelete) throws GitAPIException {
		DeleteBranchCommand deleteBranchCommand = git.branchDelete()
			.setBranchNames(branchesToDelete.toArray(new String[0]))
			// local branch can contain data which is not merged to HEAD - force
			// delete it anyway, since local copy should be R/O
			.setForce(true);
		List<String> resultList = deleteBranchCommand.call();
		this.logger
			.info(String.format("Deleted %s branches from %s branches to delete.", resultList, branchesToDelete));
		return resultList;
	}

	Ref checkout(Git git, String label) throws GitAPIException {
		CheckoutCommand checkout = git.checkout();
		if (shouldTrack(git, label)) {
			trackBranch(git, checkout, label);
		}
		else {
			// works for tags and local branches
			checkout.setName(label);
		}
		return checkout.call();
	}

	boolean shouldTrack(Git git, String label) throws GitAPIException {
		return isBranch(git, label) && !isLocalBranch(git, label);
	}

	void trackBranch(Git git, CheckoutCommand checkout, String label) {
		checkout.setCreateBranch(true)
			.setName(label)
			.setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
			.setStartPoint("origin/" + label);
	}

	boolean isBranch(Git git, String label) throws GitAPIException {
		return containsBranch(git, label, ListBranchCommand.ListMode.ALL);
	}

	boolean isLocalBranch(Git git, String label) throws GitAPIException {
		return containsBranch(git, label, null);
	}

	boolean containsBranch(Git git, String label, ListBranchCommand.ListMode listMode) throws GitAPIException {
		ListBranchCommand command = git.branchList();
		if (listMode != null) {
			command.setListMode(listMode);
		}
		List<Ref> branches = command.call();
		for (Ref ref : branches) {
			if (ref.getName().equals("refs/heads/" + label) || ref.getName().equals("refs/remotes/origin/" + label)) {
				return true;
			}
		}
		return false;
	}

	public String getDefaultLabel() {
		return defaultLabel;
	}

	public boolean isTryMasterBranch() {
		return isTryMasterBranch;
	}

	public void setTryMasterBranch(boolean tryMasterBranch) {
		isTryMasterBranch = tryMasterBranch;
	}
}
