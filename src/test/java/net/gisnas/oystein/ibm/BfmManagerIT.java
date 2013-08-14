package net.gisnas.oystein.ibm;

import java.io.File;

import org.junit.Test;

public class BfmManagerIT {

	private static final File TRUST_STORE = new File("src/test/resources/trustStore.jks");
	private static final String BFM_ENDPOINT = "https://10.0.0.6:9443/BFMJAXWSAPI/BFMJAXWSService";
	private static final String APP_NAME = "HelloBPELApp";
	private static final String USERNAME = "igor";
	private static final String PASSWORD = "Test1234";

	@Test
	public void testDeleteAllProcessInstances() {
		System.setProperty("javax.net.ssl.trustStore", TRUST_STORE.getPath());
		BfmManager bfm = new BfmManager(BFM_ENDPOINT, USERNAME, PASSWORD);
		bfm.deleteAllProcessInstances(APP_NAME);
	}

}
