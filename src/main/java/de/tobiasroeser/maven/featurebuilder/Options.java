package de.tobiasroeser.maven.featurebuilder;

import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.List;

import de.tobiasroeser.maven.shared.Option;

public class Options {

	public static final Option HELP = new Option("help", "h",
			"Display this help.");

	public static final Option USE_POM = new Option(
			"use-pom",
			null,
			"EXPERIMENTAL: Use a maven project PAR file to read feature name, version and dependencies.",
			"PAR");

	public static final Option SCAN_JARS = new Option(
			"scan-jars",
			null,
			"Create the update site with a given set of jar files in directory PAR",
			"PAR");
	public static final Option FEATURE_ID = new Option("feature-id", null,
			"Feature ID", "PAR");
	public static final Option FEATURE_VERSION = new Option("feature-version",
			null, "Feature version", "PAR");

	public static final Option CREATE_FEATURE_XML = new Option(
			"create-feature-xml", null, "Create a feature xml file PAR", "PAR");
	public static final Option UPDATE_FEATURE_XML = new Option(
			"update-feature-xml", null, "Update a feature xml file PAR", "PAR");

	public static final Option COPY_JARS_AS_BUNDLES = new Option(
			"copy-jars-as-bundles", null, "Copy found jars (via " + SCAN_JARS
					+ " option) with their bundle name to directory PAR", "PAR");

	static List<Option> allOptions() {

		LinkedList<Option> options = new LinkedList<Option>();
		try {
			for (Field field : Options.class.getDeclaredFields()) {
				options.add((Option) field.get(null));
			}
		} catch (IllegalArgumentException e) {
			throw new Error("Could not retrieve all options", e);
		} catch (IllegalAccessException e) {
			throw new Error("Could not retrieve all options", e);
		}

		return options;
	}
}
