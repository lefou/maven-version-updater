package de.tobiasroeser.maven.updatesitebuilder;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.namespace.QName;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;

import de.tobiasroeser.cmdoption.CmdOption;
import de.tobiasroeser.cmdoption.CmdOptionsParser;
import de.tobiasroeser.cmdoption.CmdOptionsParser.Result;


public class UpdateSiteBuilder {

	private final Log log = LogFactory.getLog(UpdateSiteBuilder.class);

	public static void main(String[] args) {
		try {
			final int status = new UpdateSiteBuilder().run(Arrays.asList(args));
			System.exit(status);
		} catch (Throwable t) {
			LogFactory.getLog(UpdateSiteBuilder.class).error(
					"Caught an exception.", t);
			System.exit(1);
		}
	}

	public static class Config {

		@CmdOption(longName = "site-xml", description = "The site.xml file to generateor update.", args = "PAR")
		public String siteXmlFile = "site.xml";

		@CmdOption(longName = "template-xml", description = "The template used when creating the site file.", args = "PAR")
		public String siteXmlTemplate;

		/** Map(project-key -> feature-id) */
		@CmdOption(longName = "EXPERIMAENTAL-map-artifact-to-feature", description = "Map the maven artifact PAR1 to feature id PAR2", args = {
				"PAR1", "PAR2" }, maxCount = -1)
		public final Map<String, String> projectFeatureMapping = new LinkedHashMap<String, String>();

		@CmdOption(description = "Update/bump feature id PAR1 to version PAR2", args = {
				"PAR1", "PAR2" }, maxCount = -1)
		public final Map<String, String> featureVersions = new LinkedHashMap<String, String>();
	}

	private int run(List<String> params) {
		final Config config = new Config();
		CmdOptionsParser parser = new CmdOptionsParser(Config.class);
		final Result ok = parser.parseCmdline(params, config);
		if (ok.isHelp()) {
			System.out.println(parser.formatOptions());
			return 0;
		}
		return ok.isOk() ? run(config) : ok.code();
	}

	public int run(Config config) {
		try {

			if (config.siteXmlFile != null && config.featureVersions.size() > 0) {
				updateSiteXml(config.siteXmlFile, config.siteXmlTemplate,
						config.featureVersions);
			}

		} catch (Exception e) {
			log.error("Errors occured", e);
			return 1;
		}
		return 0;
	}

	public boolean updateSiteXml(String siteXmlFile, String siteXmlTemplate,
			Map<String, String> featureVersions) {

		File tFile = null;
		if (siteXmlTemplate != null) {
			tFile = new File(siteXmlTemplate);
			if (!tFile.exists() || !tFile.isFile()) {
				log.error("Cannot find template site file: "
						+ tFile.getAbsolutePath());
				return false;
			}
		}

		File file = new File(siteXmlFile);
		if (tFile == null) {
			if (!file.exists() || !file.isFile()) {
				log.error("Cannot update not existing site file: "
						+ file.getAbsolutePath());
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
				final String featureId = cursor
						.getAttributeText(new QName("id"));
				final String featureVer = cursor.getAttributeText(new QName(
						"version"));
				if (featureVersions.containsKey(featureId)) {
					if (!featureVer.equals(featureVersions.get(featureId))) {
						log.info("Updating feature version: " + featureId + "-"
								+ featureVersions.get(featureId));
						cursor.setAttributeText(new QName("version"),
								featureVersions.get(featureId));
						cursor.setAttributeText(new QName("url"), "features/"
								+ featureId + "_"
								+ featureVersions.get(featureId) + ".jar");
					}
				}
			}

			xml.save(file, new XmlOptions().setSavePrettyPrint()
					.setSavePrettyPrintIndent(2));

		} catch (XmlException e) {
			log.error("Errors while processing xml file: "
					+ file.getAbsolutePath(), e);
			return false;
		} catch (IOException e) {
			log.error("Errors while xml file: " + file.getAbsolutePath(), e);
			return false;
		}

		return true;
	}
}
