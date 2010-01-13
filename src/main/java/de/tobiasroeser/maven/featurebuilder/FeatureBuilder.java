package de.tobiasroeser.maven.featurebuilder;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import javax.xml.namespace.QName;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.maven.pom.x400.Model;
import org.apache.maven.pom.x400.ProjectDocument;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;

import de.tobiasroeser.maven.shared.MavenXmlSupport;
import de.tobiasroeser.maven.shared.Option;
import de.tobiasroeser.maven.versionupdater.LocalArtifact;

public class FeatureBuilder {

	private final Log log = LogFactory.getLog(FeatureBuilder.class);

	public static void main(String[] args) {
		try {
			int status = new FeatureBuilder().run(Arrays.asList(args));
			// int status = new FeatureBuilder()
			// .run(Arrays
			// .asList(
			// "--feature-id",
			// "de.ibacg.cmfs.comfiscore_feature",
			// "--feature-version",
			// "1.1.4",
			// "--scan-jars",
			// "/home/lefou/work/comfis-find-errors-but-release/cmfs-budget-trunk/cmfs-budget/de.ibacg.cmfs.comfiscore_feature/plugins",
			// "--create-feature-xml",
			// "/home/lefou/work/comfis-find-errors-but-release/cmfs-budget-trunk/cmfs-budget/de.ibacg.cmfs.comfiscore_feature/test.xml"));
			System.exit(status);
		} catch (Throwable t) {
			LogFactory.getLog(FeatureBuilder.class).error(
					"Caught an exception.", t);
			System.exit(1);
		}
	}

	public static class Config {
		public String pomFile;
		public String createFeatureXml;
		public String symbolicName;
		public String version;
		public String scanJarsAtDir;
		public String updateFeatureXml;
		public String copyJarsAsBundlesTo;
	}

	private int parseCmdline(Config config, List<String> params0) {
		List<String> params = new ArrayList<String>(params0);
		int index = -1;

		index = Options.HELP.scanPosition(params);
		if (index != -1) {
			params.remove(index);
			System.out
					.println(Option.formatOptions(Options.allOptions(), null));
			return -1;
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
				log.error("Option " + Options.COPY_JARS_AS_BUNDLES
						+ " can only be used in conjunction with "
						+ Options.SCAN_JARS);
			}
			params.remove(index);
			config.copyJarsAsBundlesTo = params.get(index);
			params.remove(index);
		}

		if (params.size() > 0) {
			throw new Error("Unsupported parameters: " + params);
		}

		return 0;
	}

	private int run(List<String> params) {

		Config config = new Config();
		int ok = parseCmdline(config, params);

		return ok != 0 ? ok : run(config);
	}

	public int run(Config config) {

		try {
			List<Bundle> bundles = new LinkedList<Bundle>();

			if (config.pomFile != null) {
				readPom(config.pomFile);
			}

			List<Bundle> scannedBundles = null;
			if (config.scanJarsAtDir != null) {
				scannedBundles = scanJarsAtDir(config.scanJarsAtDir);
				log.info("jar scan found the following bundles: "
						+ scannedBundles);
				bundles.addAll(scannedBundles);
			}

			if (config.createFeatureXml != null) {
				createFeatureXml(config.createFeatureXml, config.symbolicName,
						config.version, bundles);
			}

			if (config.updateFeatureXml != null) {
				updateFeatureXml(config.updateFeatureXml, config.symbolicName,
						config.version, bundles);
			}

			if (config.copyJarsAsBundlesTo != null && scannedBundles != null) {
				copyJarsAsBundles(scannedBundles, config.copyJarsAsBundlesTo);
			}

		} catch (Exception e) {
			log.error("Errors occured", e);
			return 1;
		}
		return 0;
	}

	private void copyJarsAsBundles(List<Bundle> bundles,
			String copyJarsAsBundlesTo) {

		File dir = new File(copyJarsAsBundlesTo);
		if (dir.exists()) {
			if (!dir.isDirectory()) {
				log.error(dir.getAbsolutePath() + " is not a directory.");
				return;
			}
		} else {
			dir.mkdirs();
		}

		for (Bundle bundle : bundles) {
			File target = new File(dir, bundle.getSymbolicName() + "-"
					+ bundle.getVersion());

			FileChannel in = null;
			FileChannel out = null;

			try {
				in = new FileInputStream(bundle.getJarLocation()).getChannel();
				out = new FileOutputStream(target).getChannel();

				// According to
				// http://www.rgagnon.com/javadetails/java-0064.html
				// we cannot copy files greater than 64 MB.
				// magic number for Windows, 64Mb - 32Kb)
				int maxCount = (64 * 1024 * 1024) - (32 * 1024);
				long size = in.size();
				long position = 0;
				while (position < size) {
					position += in.transferTo(position, maxCount, out);
				}

			} catch (IOException e) {
				log.error("Error whily copying '"
						+ bundle.getJarLocation().getAbsolutePath() + "' to '"
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
				} catch (IOException e) {
					throw new RuntimeException("Could not recover from error.",
							e);
				}
			}
		}
	}

	private List<Bundle> scanJarsAtDir(String scanJarsAtDir) {
		File file = new File(scanJarsAtDir);

		LinkedList<Bundle> bundles = new LinkedList<Bundle>();

		if (!file.exists() || !file.isDirectory()) {
			log.error("Directory '" + file.getAbsolutePath()
					+ "' does not exists.");
			return bundles;
		}

		for (File jar : file.listFiles()) {
			if (jar.isFile() && jar.getName().toLowerCase().endsWith(".jar")) {
				try {
					JarInputStream jarStream = new JarInputStream(
							new BufferedInputStream(new FileInputStream(jar)));
					Manifest manifest = jarStream.getManifest();
					String symbolicName = manifest.getMainAttributes()
							.getValue("Bundle-SymbolicName");
					String version = manifest.getMainAttributes().getValue(
							"Bundle-Version");

					if (symbolicName != null && version != null) {
						symbolicName = symbolicName.split(";")[0];
						Bundle bundle = new Bundle(symbolicName, version, jar
								.length(), jar);
						bundles.add(bundle);
					} else {
						log.warn("jar '" + jar.getAbsolutePath()
								+ "' is not an OSGi bundle.");
					}

				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}

		return bundles;
	}

	private void createFeatureXml(String fileName, String symbolicName,
			String version, List<Bundle> bundles) {

		if (fileName == null || symbolicName == null || version == null
				|| bundles == null) {
			throw new IllegalArgumentException(
					"All parameters must be not null.");
		}

		XmlObject xml = XmlObject.Factory.newInstance();
		XmlCursor cursor = xml.newCursor();

		cursor.toFirstContentToken();
		cursor.beginElement("feature");
		cursor.insertAttributeWithValue("id", symbolicName);
		cursor.insertAttributeWithValue("version", version);

		cursor.insertChars("\n\t");
		cursor.beginElement("description");
		cursor.insertAttributeWithValue("url",
				"http://www.example.com/description");
		cursor.insertChars("[Enter Feature Description here.]");
		cursor.toNextToken();

		cursor.insertChars("\n\t");
		cursor.beginElement("copyright");
		cursor.insertAttributeWithValue("url",
				"http://www.example.com/copyright");
		cursor.insertChars("[Enter Copyright Description here.]");
		cursor.toNextToken();

		cursor.insertChars("\n\t");
		cursor.beginElement("license");
		cursor
				.insertAttributeWithValue("url",
						"http://www.example.com/license");
		cursor.insertChars("[Enter License Description here.]");
		cursor.toNextToken();

		cursor.insertChars("\n\t");
		cursor.beginElement("url");
		cursor.beginElement("update");
		cursor.insertAttributeWithValue("label", "Update-Site name");
		cursor.insertAttributeWithValue("url",
				"http://www.example.com/update-site");
		cursor.toNextToken();
		cursor.toNextToken();

		for (Bundle bundle : bundles) {
			insertBundle(cursor, bundle);
		}
		cursor.insertChars("\n");

		cursor.dispose();
		log.debug("Created document: " + xml.xmlText());

		File file = new File(fileName);
		log.info("Writing document to: " + file.getAbsolutePath());

		try {
			xml.save(file);
		} catch (IOException e) {
			log.error("Could not save feature file: " + file.getAbsolutePath(),
					e);
		}

	}

	private void insertBundle(XmlCursor cursor, Bundle bundle) {
		cursor.insertChars("\n\t");
		cursor.beginElement("plugin");
		cursor.insertAttributeWithValue("id", bundle.getSymbolicName());
		cursor.insertAttributeWithValue("version", bundle.getVersion());
		cursor.insertAttributeWithValue("unpack", "false");
		cursor.insertAttributeWithValue("download-size", Long.toString(bundle
				.getSizeInB()));
		cursor.insertAttributeWithValue("install-size", Long.toString(bundle
				.getSizeInB()));
		cursor.toNextToken();
	}

	private void updateFeatureXml(String fileName, String symbolicName,
			String version, List<Bundle> bundles) {

		File file = new File(fileName);
		if (!file.exists() || !file.isFile()) {
			log.error("Cannot update not existing feature file: "
					+ file.getAbsolutePath());
		}

		try {
			XmlObject xml = XmlObject.Factory.parse(file);
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
			for (Bundle bundle : bundles) {
				insertBundle(pC, bundle);
			}

			pC.dispose();

			XmlCursor tester = xml.newCursor();
			tester.selectPath("$this/feature/plugin");
			if (tester.getSelectionCount() > bundles.size()) {
				log.warn("At least "
						+ (tester.getSelectionCount() - bundles.size())
						+ " plugins of the given feature were not updated.");
			}
			tester.dispose();

			// Update feature id if necessary
			if (symbolicName != null) {
				XmlCursor idCursor = xml.newCursor();
				idCursor.selectPath("$this/feature");
				if (idCursor.getSelectionCount() == 0) {
					log.warn("Could not found feature entry.");
				}
				idCursor.toSelection(0);
				String curId = idCursor.getAttributeText(new QName("id"));
				if (!symbolicName.equals(curId)) {
					idCursor.setAttributeText(new QName("id"), symbolicName);
				}
				idCursor.dispose();
			}

			// Update feature version if necessary
			if (version != null) {
				XmlCursor versionCursor = xml.newCursor();
				versionCursor.selectPath("$this/feature");
				if (versionCursor.getSelectionCount() == 0) {
					log.warn("Could not found feature entry.");
				}
				versionCursor.toSelection(0);
				String curVersion = versionCursor.getAttributeText(new QName(
						"version"));
				if (!version.equals(curVersion)) {
					versionCursor.setAttributeText(new QName("version"),
							version);
				}
				versionCursor.dispose();
			}

			log.info("Modifying feature file: " + file.getAbsolutePath());
			xml.save(file);

		} catch (XmlException e) {
			log.error("Could not modufy feature description: "
					+ file.getAbsolutePath(), e);
		} catch (IOException e) {
			log.error("Could not modufy feature description: "
					+ file.getAbsolutePath(), e);
		}
	}

	private List<Bundle> readPom(String pomFile) {

		LinkedList<Bundle> bundles = new LinkedList<Bundle>();

		File file = new File(pomFile);
		if (!file.exists() || !file.isFile()) {
			log.error("Maven project file does not exists: "
					+ file.getAbsolutePath());
			return bundles;
		}

		try {
			ProjectDocument o = ProjectDocument.Factory.parse(file,
					MavenXmlSupport.instance.createXmlOptions());

			Model project = o.getProject();
			LocalArtifact localArtifact = MavenXmlSupport.instance
					.readLocalArtifactFromProject(project, file);

			log.debug("Analyzing Maven project: " + localArtifact);

			// Map<String, List<Dependency>> depsAndDependants =
			// MavenXmlSupport.instance
			// .readDirectDependencyFromProject(project, file);

			// FIXME: populate bundles from dependencies

			return bundles;

		} catch (XmlException e) {
			log.error("Could not read pom file: " + file.getAbsolutePath(), e);
			return bundles;
		} catch (IOException e) {
			log.error("Could not read pom file: " + file.getAbsolutePath(), e);
			return bundles;
		}

	}

}
