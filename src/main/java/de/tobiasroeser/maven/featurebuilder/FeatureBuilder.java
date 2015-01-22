package de.tobiasroeser.maven.featurebuilder;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import javax.xml.namespace.QName;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.maven.pom.x400.Model;
import org.apache.maven.pom.x400.ProjectDocument;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;

import de.tobiasroeser.maven.shared.MavenXmlSupport;
import de.tobiasroeser.maven.versionupdater.LocalArtifact;
import de.tototec.cmdoption.CmdOption;
import de.tototec.cmdoption.CmdlineParser;
import de.tototec.cmdoption.CmdlineParserException;

public class FeatureBuilder {

	private final Log log = LogFactory.getLog(FeatureBuilder.class);

	public static void main(final String[] args) {
		try {
			final int status = new FeatureBuilder().run(args);
			System.exit(status);
		} catch (final Throwable t) {
			LogFactory.getLog(FeatureBuilder.class).error("Caught an exception.", t);
			System.exit(1);
		}
	}

	public static class Config {

		@CmdOption(names = { "--help", "-h" }, description = "Show this help")
		public boolean showHelp = false;

		@CmdOption(names = "--use-pom", args = { "FILE" },
				description = "EXPERIMENTAL: Use a maven project {0} file to read feature name, version and dependencies.")
		public String pomFile;

		@CmdOption(names = "--createFeatureXml", args = { "FILE" }, description = "Create a feature xml file {0}")
		public String createFeatureXml;

		@CmdOption(names = "--feature-id", args = { "ID" }, description = "Feature {0}")
		public String symbolicName;

		@CmdOption(names = "--feature-version", args = { "VERSION" }, description = "Feature version")
		public String version;

		@CmdOption(names = "--scan-jars", args = { "DIR" },
				description = "Scan directory {0} for jars and extract bundle information (used to build the feature)")
		public String scanJarsAtDir;

		@CmdOption(names = "--update-feature-xml", args = { "FILE" }, description = "Update a feature xml file {0}")
		public String updateFeatureXml;

		@CmdOption(names = "--template-xml", args = { "FILE" },
				description = "The feature.xml template {0} file (optional for --update-feature-xml)")
		public String updateFeatureXmlTemplate;

		@CmdOption(names = "--copy-jars-as-bundles", args = { "DIR" },
				description = "Copy found jars (via --scan-jars option) with their bundle name to directory {0}")
		public String copyJarsAsBundlesTo;

		/** Map(feature-id,new-version) */
		@CmdOption(names = "--update-included-feature-version", args = { "FEATURE", "VERSION" },
				description = "Update the version of an included feature {0} to version {1}")
		public final Map<String, String> updateIncludedFeatureVersion = new LinkedHashMap<String, String>();

		/** List(feature-id) */
		@CmdOption(names = "--update-included-feature", args = { "FEATURE" },
				description = "Update the version of an included feature {0}. The version of feature PAR will be autodetected but requires the use of --scan-jars. Feature jars need the MANIFEST.MF entries 'FeatureBuilder-FeatureId' and 'FeatureBuilder-FeatureVersion'")
		public final List<String> updateIncludedFeature = new LinkedList<String>();

		@CmdOption(names = "--jar-feature", args = { "DIR" }, description = "Create a feature jar to directory {0}")
		public String jarFeatureTo;

		public List<String> validate() {
			final List<String> errors = new LinkedList<String>();
			int count = 0;
			if (createFeatureXml != null) {
				++count;
			}
			if (updateFeatureXml != null) {
				++count;
			}

			if (count == 0) {
				errors.add("No feature.xml creation/modification method given.");
			}
			if (count > 1) {
				errors.add("More that one feature.xml creation/modification methods given.");
			}

			return errors;
		}
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
			final List<Bundle> bundles = new LinkedList<Bundle>();

			if (config.pomFile != null) {
				readPom(config.pomFile);
			}

			List<Bundle> scannedBundles = null;
			if (config.scanJarsAtDir != null) {
				scannedBundles = scanBundlesAtDir(config.scanJarsAtDir);
				log.info("jar scan found the following bundles: " + scannedBundles);
				bundles.addAll(scannedBundles);

				if (config.updateIncludedFeature.size() > 0) {
					final Map<String, String> scanFeatureVersionsAtDir = scanFeatureVersionsAtDir(config.scanJarsAtDir);
					log.info("Scanned " + scanFeatureVersionsAtDir.size() + " features in jar dir: "
							+ config.scanJarsAtDir);
					for (final String featureId : config.updateIncludedFeature) {
						if (scanFeatureVersionsAtDir.containsKey(featureId)) {
							final String version = scanFeatureVersionsAtDir.get(featureId);
							log.info("Scanned feature id '" + featureId + "' with version '" + version + "'");
							config.updateIncludedFeatureVersion.put(featureId, version);
						} else {
							log.warn("Could not scan feature id: " + featureId);
						}
					}
				}
			} else {
				if (config.updateIncludedFeature.size() > 0) {
					throw new IllegalArgumentException(
							"Need option scan jars to automatically scan for included feature versions");
				}
			}

			if (config.createFeatureXml != null) {
				createFeatureXml(config.createFeatureXml, config.symbolicName, config.version, bundles);
			}

			if (config.updateFeatureXml != null) {
				updateFeatureXml(config.updateFeatureXml, config.updateFeatureXmlTemplate, config.symbolicName,
						config.version, config.updateIncludedFeatureVersion, bundles);
			}

			if (config.copyJarsAsBundlesTo != null && scannedBundles != null) {
				copyJarsAsBundles(scannedBundles, config.copyJarsAsBundlesTo);
			}

			if (config.jarFeatureTo != null) {
				createFeatureJar(config.symbolicName, config.version,
						config.createFeatureXml != null ? config.createFeatureXml : config.updateFeatureXml,
						config.jarFeatureTo);
			}

		} catch (final Exception e) {
			log.error("Errors occured", e);
			return 1;
		}
		return 0;
	}

	private boolean createFeatureJar(final String featureId, final String featureVersion, final String featureXml, final String jarDir) {

		final File file = new File(jarDir, featureId + "_" + featureVersion + ".jar");
		file.getParentFile().mkdirs();

		try {
			final JarOutputStream jarOutputStream = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(file)));

			final JarEntry entry = new JarEntry("feature.xml");
			jarOutputStream.putNextEntry(entry);

			final BufferedInputStream featureXmlStream = new BufferedInputStream(new FileInputStream(featureXml));
			copy(featureXmlStream, jarOutputStream);

			jarOutputStream.close();

		} catch (final FileNotFoundException e) {
			throw new RuntimeException("Could not create Feature Jar: " + file.getAbsolutePath(), e);
		} catch (final IOException e) {
			throw new RuntimeException("Could not create Feature Jar: " + file.getAbsolutePath(), e);
		}

		return true;
	}

	protected void copy(final InputStream in, final OutputStream out) {
		try {
			final byte[] buf = new byte[1024];
			int len;
			while ((len = in.read(buf)) > 0) {
				out.write(buf, 0, len);
			}
		} catch (final Exception e) {
			throw new RuntimeException("Error: " + e, e);
		}
	}

	private void copyJarsAsBundles(final List<Bundle> bundles, final String copyJarsAsBundlesTo) {

		final File dir = new File(copyJarsAsBundlesTo);
		if (dir.exists()) {
			if (!dir.isDirectory()) {
				log.error(dir.getAbsolutePath() + " is not a directory.");
				return;
			}
		} else {
			dir.mkdirs();
		}

		log.info("Copying " + bundles.size() + " bundles into: " + dir.getAbsolutePath());

		for (final Bundle bundle : bundles) {
			final File target = new File(dir, bundle.getSymbolicName() + "_" + bundle.getVersion() + ".jar");

			FileChannel in = null;
			FileChannel out = null;

			try {
				in = new FileInputStream(bundle.getJarLocation()).getChannel();
				out = new FileOutputStream(target).getChannel();

				// According to
				// http://www.rgagnon.com/javadetails/java-0064.html
				// we cannot copy files greater than 64 MB.
				// magic number for Windows, 64Mb - 32Kb)
				final int maxCount = (64 * 1024 * 1024) - (32 * 1024);
				final long size = in.size();
				long position = 0;
				while (position < size) {
					position += in.transferTo(position, maxCount, out);
				}

			} catch (final IOException e) {
				log.error(
						"Error whily copying '" + bundle.getJarLocation().getAbsolutePath() + "' to '"
								+ target.getAbsolutePath() + "'", e);
				return;
			} finally {
				try {
					if (in != null) {
						in.close();
					}
					if (out != null) {
						out.close();
					}
				} catch (final IOException e) {
					throw new RuntimeException("Could not recover from error.", e);
				}
			}
		}
	}

	/**
	 * @param scanJarsAtDir
	 * @return A Map(featureId -> featureVersion)
	 */
	public Map<String, String> scanFeatureVersionsAtDir(final String scanJarsAtDir) {
		final Map<String, String> featureVersions = new LinkedHashMap<String, String>();

		final File file = new File(scanJarsAtDir);
		if (!file.exists() || !file.isDirectory()) {
			log.error("Directory '" + file.getAbsolutePath() + "' does not exists.");
			return featureVersions;
		}

		for (final File jar : file.listFiles()) {
			if (jar.isFile() && jar.getName().toLowerCase().endsWith(".jar")) {
				try {
					final JarInputStream jarStream = new JarInputStream(new BufferedInputStream(new FileInputStream(jar)));
					final Manifest manifest = jarStream.getManifest();
					final String featureId = manifest.getMainAttributes().getValue("FeatureBuilder-FeatureId");
					final String featureVersion = manifest.getMainAttributes().getValue("FeatureBuilder-FeatureVersion");

					if (featureId != null && featureVersion != null) {
						featureVersions.put(featureId, featureVersion);
					}

				} catch (final FileNotFoundException e) {
					log.error("Errors while reading the Mainfest of: " + jar, e);
				} catch (final IOException e) {
					log.error("Errors while reading the Mainfest of: " + jar, e);
				}
			}
		}
		return featureVersions;
	}

	public List<Bundle> scanBundlesAtDir(final String scanJarsAtDir) {
		final File file = new File(scanJarsAtDir);

		final LinkedList<Bundle> bundles = new LinkedList<Bundle>();

		if (!file.exists() || !file.isDirectory()) {
			log.error("Directory '" + file.getAbsolutePath() + "' does not exists.");
			return bundles;
		}

		for (final File jar : file.listFiles()) {
			if (jar.isFile() && jar.getName().toLowerCase().endsWith(".jar")) {
				try {
					final JarInputStream jarStream = new JarInputStream(new BufferedInputStream(new FileInputStream(jar)));
					final Manifest manifest = jarStream.getManifest();
					String symbolicName = manifest.getMainAttributes().getValue("Bundle-SymbolicName");
					final String version = manifest.getMainAttributes().getValue("Bundle-Version");
					if (symbolicName != null && version != null) {
						symbolicName = symbolicName.split(";")[0].trim();
						final Bundle bundle = new Bundle(symbolicName, version, jar.length(), jar);
						bundles.add(bundle);
					} else {
						log.warn("jar '" + jar.getAbsolutePath() + "' is not an OSGi bundle.");
					}

				} catch (final FileNotFoundException e) {
					log.error("Errors while reading the Mainfest of: " + jar, e);
				} catch (final IOException e) {
					log.error("Errors while reading the Mainfest of: " + jar, e);
				}

			}
		}
		log.info("Found " + bundles.size() + " bundles in scanned directory: " + file.getAbsolutePath());

		Collections.sort(bundles);
		return bundles;
	}

	private void createFeatureXml(final String fileName, final String symbolicName, final String version, final List<Bundle> bundles) {

		if (fileName == null || symbolicName == null || version == null || bundles == null) {
			throw new IllegalArgumentException("All parameters must be not null.");
		}

		final XmlObject xml = XmlObject.Factory.newInstance();
		final XmlCursor cursor = xml.newCursor();

		cursor.toFirstContentToken();
		cursor.beginElement("feature");
		cursor.insertAttributeWithValue("id", symbolicName);
		cursor.insertAttributeWithValue("version", version);

		cursor.insertChars("\n\t");
		cursor.beginElement("description");
		cursor.insertAttributeWithValue("url", "http://www.example.com/description");
		cursor.insertChars("[Enter Feature Description here.]");
		cursor.toNextToken();

		cursor.insertChars("\n\t");
		cursor.beginElement("copyright");
		cursor.insertAttributeWithValue("url", "http://www.example.com/copyright");
		cursor.insertChars("[Enter Copyright Description here.]");
		cursor.toNextToken();

		cursor.insertChars("\n\t");
		cursor.beginElement("license");
		cursor.insertAttributeWithValue("url", "http://www.example.com/license");
		cursor.insertChars("[Enter License Description here.]");
		cursor.toNextToken();

		cursor.insertChars("\n\t");
		cursor.beginElement("url");
		cursor.beginElement("update");
		cursor.insertAttributeWithValue("label", "Update-Site name");
		cursor.insertAttributeWithValue("url", "http://www.example.com/update-site");
		cursor.toNextToken();
		cursor.toNextToken();

		for (final Bundle bundle : bundles) {
			insertBundle(cursor, bundle);
		}
		cursor.insertChars("\n");

		cursor.dispose();
		log.debug("Created document: " + xml.xmlText());

		final File file = new File(fileName);
		log.info("Writing document to: " + file.getAbsolutePath());

		try {
			xml.save(file);
		} catch (final IOException e) {
			log.error("Could not save feature file: " + file.getAbsolutePath(), e);
		}

	}

	private void insertBundle(final XmlCursor cursor, final Bundle bundle) {
		cursor.insertChars("\n\t");
		cursor.beginElement("plugin");
		cursor.insertAttributeWithValue("id", bundle.getSymbolicName());
		cursor.insertAttributeWithValue("version", bundle.getVersion());
		cursor.insertAttributeWithValue("unpack", "false");
		cursor.insertAttributeWithValue("download-size", Long.toString(bundle.getSizeInB()));
		cursor.insertAttributeWithValue("install-size", Long.toString(bundle.getSizeInB()));
		cursor.toNextToken();
	}

	private void updateFeatureXml(final String fileName, final String templateFile, final String symbolicName, final String version,
			final Map<String, String> includedFeatureVersions, final List<Bundle> bundles) {

		File tFile = null;
		if (templateFile != null) {
			tFile = new File(templateFile);
			if (!tFile.exists() || !tFile.isFile()) {
				log.error("Cannot read template feature file: " + tFile.getAbsolutePath());
			}
		}

		final File file = new File(fileName);
		if (tFile == null) {
			if (!file.exists() || !file.isFile()) {
				log.error("Cannot update not existing feature file: " + file.getAbsolutePath());
			}
			tFile = file;
		}

		try {
			final XmlObject xml = XmlObject.Factory.parse(tFile);
			XmlCursor pC = xml.newCursor();

			// remove existing plugin entries
			pC.selectPath("$this/feature/plugin");
			for (int i = 0; i < pC.getSelectionCount(); ++i) {
				pC.toSelection(i);
				pC.removeXml();
			}
			pC.dispose();

			// add new plugin entries
			pC = xml.newCursor();
			pC.selectPath("$this/feature");
			pC.toSelection(0);
			pC.toFirstContentToken();

			// new entries
			for (final Bundle bundle : bundles) {
				insertBundle(pC, bundle);
			}

			pC.dispose();

			final XmlCursor tester = xml.newCursor();
			tester.selectPath("$this/feature/plugin");
			if (tester.getSelectionCount() > bundles.size()) {
				log.warn("At least " + (tester.getSelectionCount() - bundles.size())
						+ " plugins of the given feature were not updated.");
			}
			tester.dispose();

			// Update feature id if necessary
			if (symbolicName != null) {
				final XmlCursor idCursor = xml.newCursor();
				idCursor.selectPath("$this/feature");
				if (idCursor.getSelectionCount() == 0) {
					log.warn("Could not found feature entry.");
				} else {
					idCursor.toSelection(0);
					final String curId = idCursor.getAttributeText(new QName("id"));
					if (!symbolicName.equals(curId)) {
						idCursor.setAttributeText(new QName("id"), symbolicName);
					}
				}
				idCursor.dispose();
			}

			// Update feature version if necessary
			if (version != null) {
				final XmlCursor versionCursor = xml.newCursor();
				versionCursor.selectPath("$this/feature");
				if (versionCursor.getSelectionCount() == 0) {
					log.warn("Could not found feature entry.");
				} else {
					versionCursor.toSelection(0);
					final String curVersion = versionCursor.getAttributeText(new QName("version"));
					if (!version.equals(curVersion)) {
						versionCursor.setAttributeText(new QName("version"), version);
					}
				}
				versionCursor.dispose();
			}

			// Update versions of included features
			if (includedFeatureVersions.size() > 0) {
				final XmlCursor includesCursor = xml.newCursor();
				includesCursor.selectPath("$this/feature/includes");
				if (includesCursor.getSelectionCount() < includedFeatureVersions.size()) {
					log.warn("Could not found some of the requested included features.");
				} else {
					for (int i = 0; i < includedFeatureVersions.size(); ++i) {
						includesCursor.toSelection(i);
						final String id = includesCursor.getAttributeText(new QName("id"));
						if (includedFeatureVersions.containsKey(id)) {
							includesCursor.setAttributeText(new QName("version"), includedFeatureVersions.get(id));
						}
					}
				}
			}

			log.info("Modifying feature file: " + file.getAbsolutePath());
			xml.save(file, new XmlOptions().setSavePrettyPrint().setSavePrettyPrintIndent(2));

		} catch (final XmlException e) {
			log.error("Could not modify feature description: " + file.getAbsolutePath(), e);
		} catch (final IOException e) {
			log.error("Could not modify feature description: " + file.getAbsolutePath(), e);
		}
	}

	private List<Bundle> readPom(final String pomFile) {

		final LinkedList<Bundle> bundles = new LinkedList<Bundle>();

		final File file = new File(pomFile);
		if (!file.exists() || !file.isFile()) {
			log.error("Maven project file does not exists: " + file.getAbsolutePath());
			return bundles;
		}

		try {
			final ProjectDocument o = ProjectDocument.Factory.parse(file, MavenXmlSupport.instance.createXmlOptions());

			final Model project = o.getProject();
			final LocalArtifact localArtifact = MavenXmlSupport.instance.readLocalArtifactFromProject(project, file);

			log.debug("Analyzing Maven project: " + localArtifact);

			// Map<String, List<Dependency>> depsAndDependants =
			// MavenXmlSupport.instance
			// .readDirectDependencyFromProject(project, file);

			// FIXME: populate bundles from dependencies

			return bundles;

		} catch (final XmlException e) {
			log.error("Could not read pom file: " + file.getAbsolutePath(), e);
			return bundles;
		} catch (final IOException e) {
			log.error("Could not read pom file: " + file.getAbsolutePath(), e);
			return bundles;
		}

	}

}
