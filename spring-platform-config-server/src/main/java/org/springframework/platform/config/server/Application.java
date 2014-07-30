package org.springframework.platform.config.server;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.Resource;
import org.springframework.security.rsa.crypto.KeyStoreKeyFactory;
import org.springframework.security.rsa.crypto.RsaSecretEncryptor;

@Configuration
@ComponentScan
@EnableAutoConfiguration
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@Configuration
	@ConfigurationProperties("encrypt")
	protected static class KeyConfiguration {
		@Autowired
		private EncryptionController controller;

		private String key;

		private KeyStore keyStore = new KeyStore();

		public String getKey() {
			return key;
		}

		public void setKey(String key) {
			this.key = key;
		}

		public KeyStore getKeyStore() {
			return keyStore;
		}

		public void setKeyStore(KeyStore keyStore) {
			this.keyStore = keyStore;
		}

		@PostConstruct
		public void init() {
			if (keyStore.getLocation() != null) {
				controller.setEncryptor(new RsaSecretEncryptor(new KeyStoreKeyFactory(
						keyStore.getLocation(), keyStore.getPassword().toCharArray())
						.getKeyPair(keyStore.getAlias())));
			}
			if (key != null) {
				controller.uploadKey(key);
			}
		}

		public static class KeyStore {

			private Resource location;
			private String password;
			private String alias;

			public String getAlias() {
				return alias;
			}

			public void setAlias(String alias) {
				this.alias = alias;
			}

			public Resource getLocation() {
				return location;
			}

			public void setLocation(Resource location) {
				this.location = location;
			}

			public String getPassword() {
				return password;
			}

			public void setPassword(String password) {
				this.password = password;
			}

		}
	}

	@Configuration
	@Profile("native")
	protected static class NativeRepositoryConfiguration {
		@Autowired
		private ConfigurableEnvironment environment;

		@Bean
		public NativeEnvironmentRepository repository() {
			return new NativeEnvironmentRepository(environment);
		}
	}

	@Configuration
	@Profile("!native")
	protected static class GitRepositoryConfiguration {
		@Autowired
		private ConfigurableEnvironment environment;

		@Bean
		@ConfigurationProperties("spring.platform.config.server")
		public JGitEnvironmentRepository repository() {
			return new JGitEnvironmentRepository(environment);
		}
	}
}
