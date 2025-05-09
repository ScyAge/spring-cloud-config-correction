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

package org.springframework.cloud.config.environment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Simple plain text serializable encapsulation of a list of property sources. Basically a
 * DTO for {@link org.springframework.core.env.Environment}, but also applicable outside
 * the domain of a Spring application.
 *
 * @author Dave Syer
 * @author Spencer Gibb
 *
 */
public class Environment {

	/**
	 * "(_)" is uncommon in a git repo name, but "/" cannot be matched by Spring MVC.
	 */
	public static final String SLASH_PLACEHOLDER = "(_)";

	private String name;

	private String[] profiles = new String[0];

	private String label;

	private List<PropertySource> propertySources = new ArrayList<>();

	private String version;

	private String state;

	public Environment(String name, String... profiles) {
		this(name, profiles, "master", null, null);
	}

	/**
	 * Copies all fields except propertySources.
	 * @param env Spring Environment
	 */
	public Environment(Environment env) {
		this(env.getName(), env.getProfiles(), env.getLabel(), env.getVersion(), env.getState());
	}

	@JsonCreator
	public Environment(@JsonProperty("name") String name, @JsonProperty("profiles") String[] profiles,
			@JsonProperty("label") String label, @JsonProperty("version") String version,
			@JsonProperty("state") String state) {
		super();
		this.name = name;
		this.profiles = profiles;
		this.label = label;
		this.version = version;
		this.state = state;
	}

	/**
	 * Utility method for normalizing names and labels.
	 * @param s String to normalize.
	 * @return if s contains (_), replace with slash.
	 */
	public static String normalize(String s) {
		if (s != null && s.contains(SLASH_PLACEHOLDER)) {
			// "(_)" is uncommon in a git repo name, but "/" cannot be matched
			// by Spring MVC
			return s.replace(SLASH_PLACEHOLDER, "/");
		}
		return s;
	}

	/**
	 * Utility method for denormalizing names and labels.
	 * @param s String to denormalize.
	 * @return if s contains slash, replace with (_).
	 */
	public static String denormalize(String s) {
		if (s != null && s.contains("/")) {
			return s.replace("/", SLASH_PLACEHOLDER);
		}
		return s;
	}

	public void add(PropertySource propertySource) {
		this.propertySources.add(propertySource);
	}

	public void addAll(List<PropertySource> propertySources) {
		this.propertySources.addAll(propertySources);
	}

	public void addFirst(PropertySource propertySource) {
		this.propertySources.add(0, propertySource);
	}

	public List<PropertySource> getPropertySources() {
		return this.propertySources;
	}

	public String getName() {
		return this.name;
	}



	public String getLabel() {
		return this.label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public String[] getProfiles() {
		return this.profiles;
	}


	public String getVersion() {
		return this.version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public String getState() {
		return this.state;
	}

	public void setState(String state) {
		this.state = state;
	}

	@Override
	public String toString() {
		return "Environment [name=" + this.name + ", profiles=" + Arrays.asList(this.profiles) + ", label=" + this.label
				+ ", propertySources=" + this.propertySources + ", version=" + this.version + ", state=" + this.state
				+ "]";
	}

}
