package org.springframework.cloud.config.server.environment;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.TransportCommand;
import org.eclipse.jgit.api.errors.GitAPIException;

public interface JGitRepositoryInterfaceMethodeForSync{

	boolean isBranch(Git git, String label) throws GitAPIException;

	boolean isClean(Git git, String label);


	void warn(String message, Exception ex);

	void logDirty(Status gitStatus);

	boolean isDeleteUntrackedBranches();

	void configureCommand(TransportCommand<?, ?> command);
}
