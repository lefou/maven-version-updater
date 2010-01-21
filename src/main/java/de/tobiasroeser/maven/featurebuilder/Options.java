package de.tobiasroeser.maven.featurebuilder;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.logging.LogFactory;

import de.tobiasroeser.maven.featurebuilder.FeatureBuilder.Config;
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
			"Scan directory PAR for jars and extract bundle information (used to build the feature)",
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

	public static final Option UPDATE_INCLUDED_FEATURE_VERSION = new Option(
			"update-included-feature-version", null,
			"Update the version of an included feature PAR1 to version PAR2",
			"PAR1", "PAR");

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

	public static final int EXIT_OK = 0;
	public static final int EXIT_HELP = -1;
	public static final int EXIT_INVALID_CMDLINE = 1;

	public static int parseCmdline(Config config, List<String> cmdLine) {
		List<String> params = new ArrayList<String>(cmdLine);
		int index = -1;

		index = Options.HELP.scanPosition(params);
		if (index != -1) {
			params.remove(index);
			System.out
					.println(Option.formatOptions(Options.allOptions(), null));
			return EXIT_HELP;
		}

		index = Options.USE_POM.scanPosition(params);
		if (index != -1) {
			params.remove(index);
			config.pomFile = params.get(index);
			params.remove(index);
		}

		index = Options.CREATE_FEATURE_XML.scanPosition(params);
		if (index != -1) {
			params.remove(index);
			config.createFeatureXml = params.get(index);
			params.remove(index);
		}

		index = Options.UPDATE_FEATURE_XML.scanPosition(params);
		if (index != -1) {
			params.remove(index);
			config.updateFeatureXml = params.get(index);
			params.remove(index);
		}

		index = Options.SCAN_JARS.scanPosition(params);
		if (index != -1) {
			params.remove(index);
			config.scanJarsAtDir = params.get(index);
			params.remove(index);
		}

		index = Options.FEATURE_ID.scanPosition(params);
		if (index != -1) {
			params.remove(index);
			config.symbolicName = params.get(index);
			params.remove(index);
		}

		index = Options.FEATURE_VERSION.scanPosition(params);
		if (index != -1) {
			params.remove(index);
			config.version = params.get(index);
			params.remove(index);
		}

		index = Options.COPY_JARS_AS_BUNDLES.scanPosition(params);
		if (index != -1) {
			if (config.scanJarsAtDir == null) {
				LogFactory.getLog(Options.class).error(
						"Option " + Options.COPY_JARS_AS_BUNDLES
								+ " can only be used in conjunction with "
								+ Options.SCAN_JARS);
				return EXIT_INVALID_CMDLINE;
			}
			params.remove(index);
			config.copyJarsAsBundlesTo = params.get(index);
			params.remove(index);
		}

		index = Options.UPDATE_INCLUDED_FEATURE_VERSION.scanPosition(params);
		if (index != -1) {
			params.remove(index);
			config.updateIncludedFeatureVersion.put(params.get(index), params
					.get(index + 1));
			params.remove(index);
			params.remove(index);
		}

		if (config.createFeatureXml != null && config.updateFeatureXml != null) {
			LogFactory.getLog(Options.class).error(
					"Cannot update and create a feature,xml at the same time.");
			return EXIT_INVALID_CMDLINE;
		}

		if (params.size() > 0) {
			LogFactory.getLog(Options.class).error("Unsupported parameters: " + params);
			return EXIT_INVALID_CMDLINE;
		}

		return EXIT_OK;
	}
}
