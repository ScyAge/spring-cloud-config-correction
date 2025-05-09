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

package org.springframework.cloud.config.client;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.cloud.config.client.ConfigClientProperties.Credentials;
import org.springframework.cloud.config.client.ConfigClientProperties.MultipleUriStrategy;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
public class ConfigClientPropertiesTests {

	private ConfigClientProperties locator = new ConfigClientProperties(new StandardEnvironment());

	@Test
	public void vanilla() {
		this.locator.setUri(new String[] { "http://localhost:9999" });
		this.locator.setPassword("secret");
		Credentials credentials = this.locator.getCredentials(0);
		assertThat(credentials.getUri()).isEqualTo("http://localhost:9999");
		assertThat(credentials.getUsername()).isEqualTo("user");
		assertThat(credentials.getPassword()).isEqualTo("secret");
	}

	@Test
	public void uriCreds() {
		this.locator.setUri(new String[] { "http://foo:bar@localhost:9999" });
		Credentials credentials = this.locator.getCredentials(0);
		assertThat(credentials.getUri()).isEqualTo("http://localhost:9999");
		assertThat(credentials.getUsername()).isEqualTo("foo");
		assertThat(credentials.getPassword()).isEqualTo("bar");
	}

	@Test
	public void uriCredsWithAtInPassword() {
		this.locator.setUri(new String[] { "http://foo:bar%40@localhost:9999" });
		Credentials credentials = this.locator.getCredentials(0);
		assertThat(credentials.getUri()).isEqualTo("http://localhost:9999");
		assertThat(credentials.getUsername()).isEqualTo("foo");
		assertThat(credentials.getPassword()).isEqualTo("bar@");
	}

	@Test
	public void explicitPassword() {
		this.locator.setUri(new String[] { "http://foo:bar@localhost:9999" });
		this.locator.setPassword("secret");
		Credentials credentials = this.locator.getCredentials(0);
		assertThat(credentials.getUri()).isEqualTo("http://localhost:9999");
		assertThat(credentials.getUsername()).isEqualTo("foo");
		assertThat(credentials.getPassword()).isEqualTo("secret");
	}

	@Test
	public void testIfNoColonPresentInUriCreds() {
		this.locator.setUri(new String[] { "http://foobar@localhost:9999" });
		this.locator.setPassword("secret");
		Credentials credentials = this.locator.getCredentials(0);
		assertThat(credentials.getUri()).isEqualTo("http://localhost:9999");
		assertThat(credentials.getUsername()).isEqualTo("foobar");
		assertThat(credentials.getPassword()).isEqualTo("secret");
	}

	@Test
	public void testIfColonPresentAtTheEndInUriCreds() {
		this.locator.setUri(new String[] { "http://foobar:@localhost:9999" });
		this.locator.setPassword("secret");
		Credentials credentials = this.locator.getCredentials(0);
		assertThat(credentials.getUri()).isEqualTo("http://localhost:9999");
		assertThat(credentials.getUsername()).isEqualTo("foobar");
		assertThat(credentials.getPassword()).isEqualTo("secret");
	}

	@Test
	public void testIfColonPresentAtTheStartInUriCreds() {
		this.locator.setUri(new String[] { "http://:foobar@localhost:9999" });
		Credentials credentials = this.locator.getCredentials(0);
		assertThat(credentials.getUri()).isEqualTo("http://localhost:9999");
		assertThat(credentials.getUsername()).isEqualTo("");
		assertThat(credentials.getPassword()).isEqualTo("foobar");
	}

	@Test
	public void testIfColonPresentAtTheStartAndEndInUriCreds() {
		this.locator.setUri(new String[] { "http://:foobar:@localhost:9999" });
		Credentials credentials = this.locator.getCredentials(0);
		assertThat(credentials.getUri()).isEqualTo("http://localhost:9999");
		assertThat(credentials.getUsername()).isEqualTo("");
		assertThat(credentials.getPassword()).isEqualTo("foobar:");
	}

	@Test
	public void testIfSpacePresentAsUriCreds() {
		this.locator.setUri(new String[] { "http://  @localhost:9999" });
		this.locator.setPassword("secret");
		Credentials credentials = this.locator.getCredentials(0);
		assertThat(credentials.getUri()).isEqualTo("http://localhost:9999");
		assertThat(credentials.getUsername()).isEqualTo("  ");
		assertThat(credentials.getPassword()).isEqualTo("secret");
	}

	@Test
	public void changeNameInOverride() {
		this.locator.setName("one");
		ConfigurableEnvironment environment = new StandardEnvironment();
		TestPropertyValues.of("spring.application.name:two").applyTo(environment);
		ConfigClientProperties override = this.locator.override(environment);
		assertThat(override.getName()).isEqualTo("two");
	}

	@Test
	public void testThatExplicitUsernamePasswordTakePrecedence() {
		ConfigClientProperties properties = new ConfigClientProperties(new MockEnvironment());

		properties.setUri(new String[] { "https://userInfoName:userInfoPW@localhost:8888/" });
		properties.setUsername("explicitName");
		properties.setPassword("explicitPW");
		Credentials credentials = properties.getCredentials(0);
		assertThat(credentials.getPassword()).isEqualTo("explicitPW");
		assertThat(credentials.getUsername()).isEqualTo("explicitName");
	}

	@Test
	public void checkIfExceptionThrownForNegativeIndex() {
		Assertions.assertThatThrownBy(() -> {
			this.locator.setUri(new String[] { "http://localhost:8888", "http://localhost:8889" });

		}).isInstanceOf(IllegalStateException.class).hasMessageContaining("Trying to access an invalid array index");
	}

	@Test
	public void checkIfExceptionThrownForPositiveInvalidIndex() {
		Assertions.assertThatThrownBy(() -> {
			this.locator.setUri(new String[] { "http://localhost:8888", "http://localhost:8889" });
		}).isInstanceOf(IllegalStateException.class).hasMessageContaining("Trying to access an invalid array index");
	}

	@Test
	public void checkIfExceptionThrownForIndexEqualToLength() {
		Assertions.assertThatThrownBy(() -> {
			this.locator.setUri(new String[] { "http://localhost:8888", "http://localhost:8889" });
		}).isInstanceOf(IllegalStateException.class).hasMessageContaining("Trying to access an invalid array index");
	}

	@Test
	public void testThatDefaultMultipleUriStrategyIsAlways() {
		ConfigClientProperties properties = new ConfigClientProperties(new MockEnvironment());
		assertThat(properties.getMultipleUriStrategy()).isNotNull();
		assertThat(properties.getMultipleUriStrategy().name()).isEqualTo(MultipleUriStrategy.ALWAYS.name());
	}

	@Test
	public void testThatExplicitMultipleUriStrategyTakesPrecedence() {
		ConfigClientProperties properties = new ConfigClientProperties(new MockEnvironment());
		properties.setMultipleUriStrategy(MultipleUriStrategy.CONNECTION_TIMEOUT_ONLY);
		assertThat(properties.getMultipleUriStrategy()).isNotNull();
		assertThat(properties.getMultipleUriStrategy().name())
			.isEqualTo(MultipleUriStrategy.CONNECTION_TIMEOUT_ONLY.name());
	}

}
