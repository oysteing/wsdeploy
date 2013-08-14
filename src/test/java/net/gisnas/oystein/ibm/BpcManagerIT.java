package net.gisnas.oystein.ibm;

import java.io.File;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.ibm.websphere.management.AdminClient;

public class BpcManagerIT {

	private static final File TRUST_STORE = new File("src/test/resources/trustStore.jks");
	private static final String APP_NAME = "HelloBPELApp";

	private BpcManager bm;

	@Before
	public void setUp() throws Exception {
		System.setProperty("javax.net.ssl.trustStore", TRUST_STORE.getPath());
		AdminClientConnectorProperties properties = new AdminClientConnectorProperties("10.0.0.6", 8880, "igor", "Test1234");
		AdminClient adminClient = AdminClientConnectorProperties.createAdminClient(properties);
		bm = new BpcManager(adminClient);
	}

	@Test
	public void testGetProcessTemplates() {
		Set<ProcessTemplate> templates = bm.getProcessTemplates(APP_NAME);
		Assert.assertEquals(templates.size(), 1);
	}
	
	@Test
	public void testStopProcessTemplates() {
		bm.stopProcessTemplates(APP_NAME);
	}
	
	@Test
	public void testUniqueDeploymentTarget() {
		bm.uniqueDeploymentTarget(APP_NAME);
	}

}
