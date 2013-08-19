package net.gisnas.oystein.ibm;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

public class ScaUtilTest {

	private static final File earFile = new File("src/test/resources/HelloBPEL.ear");

	@Test
	public void testModifyWsImports() throws IOException {
		ImportEndpoint importEndpoint = new ImportEndpoint();
		importEndpoint.importName = "WSImport1";
		importEndpoint.endpointUrl = "http://www.example.org";
		ImportEndpoint[] importEndpoints = new ImportEndpoint[] { importEndpoint };
		File targetFile = File.createTempFile("ScaUtilTest", null);

		ScaUtil.modifyWsImports(importEndpoints, earFile, targetFile);
	}

}
