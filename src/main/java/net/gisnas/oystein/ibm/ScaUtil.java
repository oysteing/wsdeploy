package net.gisnas.oystein.ibm;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

public class ScaUtil {

	private static Logger log = LoggerFactory.getLogger(ScaUtil.class);

	public static void modifyWsImports(ImportEndpoint[] importEndpoints, File earFile, File targetFile) throws ZipException, IOException {
		ZipFile zipFile = new ZipFile(earFile);
		try {
			ZipEntry scaModuleEntry = findScaModule(zipFile);
			ImportSet importXmls = findWsImports(zipFile, scaModuleEntry);
			modifyEndpoints(importXmls, importEndpoints);
			writeArchive(importXmls, zipFile, targetFile, scaModuleEntry);
		} finally {
			IOUtils.closeQuietly(zipFile);
		}
	}

	private static ZipEntry findScaModule(ZipFile zipFile) throws IOException {
		Enumeration<? extends ZipEntry> entries = zipFile.entries();
		while (entries.hasMoreElements()) {
			ZipEntry entry = entries.nextElement();
			if (entry.getName().endsWith(".jar")) {
				ZipInputStream is = new ZipInputStream(zipFile.getInputStream(entry));
				try {
					if (isScaModule(is)) {
						return entry;
					}
				} finally {
					IOUtils.closeQuietly(is);
				}
			}
		}
		throw new RuntimeException("No SCA module found in " + zipFile.getName());
	}

	private static boolean isScaModule(ZipInputStream is) throws IOException {
		ZipEntry e;
		while ((e = is.getNextEntry()) != null) {
			if (e.getName().endsWith(".module") || e.getName().endsWith(".MODULE")) {
				return true;
			}
		}
		return false;
	}

	private static ImportSet findWsImports(ZipFile zipFile, ZipEntry scaModuleEntry) throws IOException {
		try {
			DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			ImportSet imports = new ImportSet();
			ZipInputStream is = new ZipInputStream(zipFile.getInputStream(scaModuleEntry));
			try {
				ZipEntry entry;
				while ((entry = is.getNextEntry()) != null) {
					if (entry.getName().endsWith(".import")) {
						try {
							byte[] content = IOUtils.toByteArray(is);
							Document doc = db.parse(new ByteArrayInputStream(content));
							String name = doc.getDocumentElement().getAttribute("name");
							imports.put(name, entry.getName(), doc);
						} catch (SAXException e) {
							log.warn("Unable to parse {} - skipping", entry.getName(), e);
						}
					}
				}
			} finally {
				IOUtils.closeQuietly(is);
			}
			return imports;
		} catch (ParserConfigurationException e) {
			throw new RuntimeException("Unable to create document builder", e);
		}
	}

	private static void modifyEndpoints(ImportSet importXmls, ImportEndpoint[] importEndpoints) {
		Set<String> importEndpointsSet = new HashSet<>();
		for (ImportEndpoint importEndpoint : importEndpoints) {
			importEndpointsSet.add(importEndpoint.importName);
		}
		Set<String> imports = importXmls.nameKeySet();
		if (!imports.containsAll(importEndpointsSet)) {
			log.error("Mismatch between found and specified import names. {} was specified, but only found {} in SCDL files.", importEndpointsSet, imports);
			throw new RuntimeException("Mismatch between found and specified import names");
		}
		for (ImportEndpoint importEndpoint : importEndpoints) {
			Document doc = importXmls.getByName(importEndpoint.importName);
			doc.getDocumentElement().getElementsByTagName("esbBinding").item(0).getAttributes().getNamedItem("endpoint")
					.setNodeValue(importEndpoint.endpointUrl);
		}
		log.debug("Set endpoint address for {} imports", imports.size());
	}

	private static void writeArchive(ImportSet importXmls, ZipFile zipFile, File destZipFile, ZipEntry scaModuleEntry) {
		log.info("Writing modified ear file to {}", destZipFile);
		try {
			final ZipOutputStream destZip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(destZipFile)));
			try {
				Enumeration<? extends ZipEntry> entries = zipFile.entries();
				while (entries.hasMoreElements()) {
					ZipEntry entry = entries.nextElement();
					if (entry.getName().equals(scaModuleEntry.getName())) {
						destZip.putNextEntry(new ZipEntry(scaModuleEntry.getName()));
						byte[] scaModuleJar = createScaModuleJar(zipFile, entry, importXmls);
						IOUtils.write(scaModuleJar, destZip);
					} else {
						destZip.putNextEntry(entry);
						InputStream is = zipFile.getInputStream(entry);
						try {
							IOUtils.copy(is, destZip);
						} finally {
							IOUtils.closeQuietly(is);
						}
					}
				}
			} finally {
				IOUtils.closeQuietly(destZip);
			}
		} catch (IOException e) {
			throw new RuntimeException("An error occured while creating " + destZipFile, e);
		}
	}

	private static byte[] createScaModuleJar(ZipFile zipFile, ZipEntry scaEntry, ImportSet importXmls) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			ZipInputStream origJar = new ZipInputStream(zipFile.getInputStream(scaEntry));
			final ZipOutputStream destZip = new ZipOutputStream(baos);
			ZipEntry entry;
			try {
				while ((entry = origJar.getNextEntry()) != null) {
					if (importXmls.entryNameKeySet().contains(entry.getName())) {
						try {
							Transformer transformer = TransformerFactory.newInstance().newTransformer();
							Document doc = importXmls.getByEntryName(entry.getName());
							ByteArrayOutputStream out = new ByteArrayOutputStream();
							try {
								transformer.transform(new DOMSource(doc), new StreamResult(out));
							} finally {
								IOUtils.closeQuietly(out);
							}
							destZip.putNextEntry(new ZipEntry(entry.getName()));
							IOUtils.write(out.toByteArray(), destZip);
						} catch (TransformerFactoryConfigurationError | TransformerException e) {
							throw new RuntimeException("Unable to serialize XML document", e);
						}
					} else {
						destZip.putNextEntry(entry);
						IOUtils.copy(origJar, destZip);
					}
				}
			} finally {
				IOUtils.closeQuietly(origJar);
				IOUtils.closeQuietly(destZip);
			}
			return baos.toByteArray();
		} catch (IOException e) {
			throw new RuntimeException("Unable to create SCA module archive", e);
		} finally {
			IOUtils.closeQuietly(baos);
		}
	}

}
