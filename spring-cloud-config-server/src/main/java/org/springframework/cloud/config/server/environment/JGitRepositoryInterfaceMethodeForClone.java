package org.springframework.cloud.config.server.environment;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.TransportCommand;
import org.eclipse.jgit.api.errors.GitAPIException;

public interface JGitRepositoryInterfaceMethodeForClone {

	void checkoutDefaultBranchWithRetry(Git git)  throws GitAPIException;

	void configureCommand(TransportCommand<?, ?> command);

	String getUri();
}
