package de.tobiasroeser.maven.updatesitebuilder;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.namespace.QName;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;

import de.tototec.cmdoption.CmdOption;
import de.tototec.cmdoption.CmdlineParser;
import de.tototec.cmdoption.CmdlineParserException;

public class UpdateSiteBuilder {

	private final Log log = LogFactory.getLog(UpdateSiteBuilder.class);

	public static void main(final String[] args) {
		try {
			final int status = new UpdateSiteBuilder().run(args);
			System.exit(status);
		} catch (final Throwable t) {
			LogFactory.getLog(UpdateSiteBuilder.class).error("Caught an exception.", t);
			System.exit(1);
		}
	}

	public static class Config {
		@CmdOption(names = { "--help", "-h" }, description = "Show this help")
		public boolean showHelp = false;

		@CmdOption(names = "--site-xml", args = { "FILE" }, description = "The site.xml file to generate or update.")
		public String siteXmlFile = "site.xml";

		@CmdOption(names = "--template-xml", args = { "FILE" }, description = "The template used when creating the site file.")
		public String siteXmlTemplate;

		// /** Map(project-key -> feature-id) */
		// @Parameter(names = "--EXPERIMAENTAL-map-artifact-to-feature",
		// description = "Map the maven artifact PAR1 to feature id PAR2", arity
		// = 2)
		// public final List<String> projectFeatureMapping = new
		// LinkedList<String>();

		@CmdOption(names = "--feature-versions", args = { "ID", "VERSION" }, description = "Update/bump feature id {0} to version {1}")
		public final Map<String, String> featureVersions = new LinkedHashMap<String, String>();

	}

	private int run(final String[] params) {
		final Config config = new Config();

		final CmdlineParser cp = new CmdlineParser(config);
		try {
			cp.parse(params);
		} catch (final CmdlineParserException e) {
			System.err.println(e.getMessage());
			return 1;
		}
		if (config.showHelp) {
			cp.usage();
			return 0;
		}
		return run(config);
	}

	public int run(final Config config) {
		try {

			if (config.siteXmlFile != null && config.featureVersions.size() > 0) {
				updateSiteXml(config.siteXmlFile, config.siteXmlTemplate, config.featureVersions);
			}

		} catch (final Exception e) {
			log.error("Errors occured", e);
			return 1;
		}
		return 0;
	}

	public boolean updateSiteXml(final String siteXmlFile, final String siteXmlTemplate, final Map<String, String> featureVersions) {

		File tFile = null;
		if (siteXmlTemplate != null) {
			tFile = new File(siteXmlTemplate);
			if (!tFile.exists() || !tFile.isFile()) {
				log.error("Cannot find template site file: " + tFile.getAbsolutePath());
				return false;
			}
		}

		final File file = new File(siteXmlFile);
		if (tFile == null) {
			if (!file.exists() || !file.isFile()) {
				log.error("Cannot update not existing site file: " + file.getAbsolutePath());
				return false;
			}
			tFile = file;
		}

		if (featureVersions.size() == 0) {
			log.warn("Empty mapping, nothing to update.");
			return true;
		}

		try {
			final XmlObject xml = XmlObject.Factory.parse(tFile);
			final XmlCursor cursor = xml.newCursor();

			cursor.selectPath("$this/site/feature");
			for (int i = 0; i < cursor.getSelectionCount(); ++i) {
				cursor.toSelection(i);
				final String featureId = cursor.getAttributeText(new QName("id"));
				final String featureVer = cursor.getAttributeText(new QName("version"));
				if (featureVersions.containsKey(featureId)) {
					if (!featureVer.equals(featureVersions.get(featureId))) {
						log.info("Updating feature version: " + featureId + "-" + featureVersions.get(featureId));
						cursor.setAttributeText(new QName("version"), featureVersions.get(featureId));
						cursor.setAttributeText(new QName("url"),
								"features/" + featureId + "_" + featureVersions.get(featureId) + ".jar");
					}
				}
			}

			xml.save(file, new XmlOptions().setSavePrettyPrint().setSavePrettyPrintIndent(2));

		} catch (final XmlException e) {
			log.error("Errors while processing xml file: " + file.getAbsolutePath(), e);
			return false;
		} catch (final IOException e) {
			log.error("Errors while xml file: " + file.getAbsolutePath(), e);
			return false;
		}

		return true;
	}
}
