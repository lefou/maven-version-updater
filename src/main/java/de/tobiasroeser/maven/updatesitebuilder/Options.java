package de.tobiasroeser.maven.updatesitebuilder;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.logging.LogFactory;

import de.tobiasroeser.maven.shared.Option;
import de.tobiasroeser.maven.updatesitebuilder.UpdateSiteBuilder.Config;

public class Options {

	public static final Option HELP = new Option("help", "h",
			"Display this help.");

	public static final Option MAP_PROJECT_TO_FEATURE = new Option(
			"EXPERIMAENTAL-map-artifact-to-feature", null,
			"Map the maven artifact PAR1 to feature id PAR2", "PAR1", "PAR2");

	public static final Option FEATURE_VERSIONS = new Option("feature-version",
			null, "Update/bump feature id PAR1 to version PAR2", "PAR1", "PAR2");

	static List<Option> allOptions() {
		LinkedList<Option> options = new LinkedList<Option>();
		try {
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
			System.out
					.println(Option.formatOptions(Options.allOptions(), null, true));
			return EXIT_HELP;
		}

		while ((index = Options.MAP_PROJECT_TO_FEATURE.scanPosition(params)) != -1) {
			params.remove(index);
			config.projectFeatureMapping.put(params.get(index), params
					.get(index + 1));
			params.remove(index);
			params.remove(index);
		}

		while ((index = Options.FEATURE_VERSIONS.scanPosition(params)) != -1) {
			params.remove(index);
			config.featureVersions
					.put(params.get(index), params.get(index + 1));
			params.remove(index);
			params.remove(index);
		}

		if (params.size() > 0) {
			LogFactory.getLog(Options.class).error(
					"Unsupported parameters: " + params);
			return EXIT_INVALID_CMDLINE;
		}

		return EXIT_OK;
	}
}
