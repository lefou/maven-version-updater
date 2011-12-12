package de.tobiasroeser.maven.updatesitebuilder;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.xml.namespace.QName;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import de.tobiasroeser.maven.shared.JCommanderSupport;

public class UpdateSiteBuilder {

	private final Log log = LogFactory.getLog(UpdateSiteBuilder.class);

	public static void main(String[] args) {
		try {
			final int status = new UpdateSiteBuilder().run(args);
			System.exit(status);
		} catch (Throwable t) {
			LogFactory.getLog(UpdateSiteBuilder.class).error("Caught an exception.", t);
			System.exit(1);
		}
	}

	public static class Config {
		@Parameter(names = { "--help", "-h" }, description = "Show this help")
		public boolean showHelp = false;

		@Parameter(names = "--site-xml", description = "The site.xml file to generateor update.")
		public String siteXmlFile = "site.xml";

		@Parameter(names = "--template-xml", description = "The template used when creating the site file.")
		public String siteXmlTemplate;

		// /** Map(project-key -> feature-id) */
		// @Parameter(names = "--EXPERIMAENTAL-map-artifact-to-feature",
		// description = "Map the maven artifact PAR1 to feature id PAR2", arity
		// = 2)
		// public final List<String> projectFeatureMapping = new
		// LinkedList<String>();

		@Parameter(names = "--feature-versions", description = "Update/bump feature id PAR1 to version PAR2", arity = 2)
		public final List<String> featureVersions = new LinkedList<String>();

	}

	private int run(String[] params) {
		final Config config = new Config();

		JCommander jc = new JCommander(config);
		jc.parse(params);
		if (config.showHelp) {
			jc.usage();
			return 0;
		}
		return run(config);
	}

	public int run(Config config) {
		try {

			if (config.siteXmlFile != null && config.featureVersions.size() > 0) {
				updateSiteXml(config.siteXmlFile, config.siteXmlTemplate, JCommanderSupport.toMap(config.featureVersions));
			}

		} catch (Exception e) {
			log.error("Errors occured", e);
			return 1;
		}
		return 0;
	}

	public boolean updateSiteXml(String siteXmlFile, String siteXmlTemplate, Map<String, String> featureVersions) {

		File tFile = null;
		if (siteXmlTemplate != null) {
			tFile = new File(siteXmlTemplate);
			if (!tFile.exists() || !tFile.isFile()) {
				log.error("Cannot find template site file: " + tFile.getAbsolutePath());
				return false;
			}
		}

		File file = new File(siteXmlFile);
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
			XmlObject xml = XmlObject.Factory.parse(tFile);
			XmlCursor cursor = xml.newCursor();

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

		} catch (XmlException e) {
			log.error("Errors while processing xml file: " + file.getAbsolutePath(), e);
			return false;
		} catch (IOException e) {
			log.error("Errors while xml file: " + file.getAbsolutePath(), e);
			return false;
		}

		return true;
	}
}
