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

	public static final Option TEMPLATE_XML = new Option("template-xml", null,
			"The feature.xml template PAR file (optional for "
					+ Options.UPDATE_FEATURE_XML + ")", "PAR");
	
	public static final Option UPDATE_FEATURE_XML = new Option(
			"update-feature-xml", null, "Update a feature xml file PAR", "PAR");

	public static final Option COPY_JARS_AS_BUNDLES = new Option(
			"copy-jars-as-bundles", null, "Copy found jars (via " + SCAN_JARS
					+ " option) with their bundle name to directory PAR", "PAR");

	public static final Option UPDATE_INCLUDED_FEATURE_VERSION = new Option(
			"update-included-feature-version", null,
			"Update the version of an included feature PAR1 to version PAR2",
			"PAR1", "PAR");
	public static final Option UPDATE_INCLUDED_FEATURE = new Option(
			"update-included-feature-version",
			null,
			"Update the version of an included feature PAR. The version of feature PAR will be autodetected but required the use of "
					+ SCAN_JARS
					+ ". Feature jars need the MANIFEST.MF entries 'FeatureBuilder-FeatureId' and 'FeatureBuilder-FeatureVersion'",
			"PAR");

	public static final Option JAR_FEATURE = new Option("jar-feature", null,
			"Create a feature jar to directory PAR", "PAR");

	static List<Option> allOptions() {

		LinkedList<Option> options = new LinkedList<Option>();
		try {
			List<Option> annotatedOptions = Option
					.scanCmdOpions(FeatureBuilder.Config.class);
			if (annotatedOptions != null && annotatedOptions.size() > 0) {
				options.addAll(options);
			}
			for (Field field : Options.class.getDeclaredFields()) {
				if (field.getType().equals(Option.class)) {
					options.add((Option) field.get(null));
				}
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
			System.out.println(Option.formatOptions(Options.allOptions(), null,
					true));
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
		index = Options.TEMPLATE_XML.scanPosition(params);
		if (index != -1) {
			params.remove(index);
			config.updateFeatureXmlTemplate = params.get(index);
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

		index = Options.JAR_FEATURE.scanPosition(params);
		if (index != -1) {
			params.remove(index);
			config.jarFeatureTo = params.get(index);
			params.remove(index);
		}

		while ((index = Options.UPDATE_INCLUDED_FEATURE_VERSION
				.scanPosition(params)) != -1) {
			params.remove(index);
			config.updateIncludedFeatureVersion.put(params.get(index), params
					.get(index + 1));
			params.remove(index);
			params.remove(index);
		}

		while ((index = Options.UPDATE_INCLUDED_FEATURE.scanPosition(params)) != -1) {
			params.remove(index);
			config.updateIncludedFeature.add(params.get(index));
			params.remove(index);
		}

		if (config.createFeatureXml != null && config.updateFeatureXml != null) {
			LogFactory.getLog(Options.class).error(
					"Cannot update and create a feature,xml at the same time.");
			return EXIT_INVALID_CMDLINE;
		}

		final List<String> validate = config.validate();
		if (validate.size() > 0) {
			for (String error : validate) {
				LogFactory.getLog(Options.class).error(
						"Invalid commandline: " + error);
			}
			return EXIT_INVALID_CMDLINE;
		}

		if (params.size() > 0) {
			LogFactory.getLog(Options.class).error(
					"Unsupported parameters: " + params);
			return EXIT_INVALID_CMDLINE;
		}

		return EXIT_OK;
	}
}
