package net.gisnas.oystein.ibm;

import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Before;
import org.junit.Test;

import com.ibm.websphere.management.AdminClient;

public class AppManagementClientIT {

	private static final File TRUST_STORE = new File("/home/oysteigi/src/ibmdeploy/wsdeploy/src/test/resources/trustStore.jks");
	private static final String EAR_FILE = "src/test/resources/echoear-0.0.1-SNAPSHOT.ear";
	private static final String APP_NAME = "echoear";

	private AppManagementClient amClient;

	@Before
	public void setUp() {
		System.setProperty("javax.net.ssl.trustStore", TRUST_STORE.getPath());
		AdminClientConnectorProperties properties = new AdminClientConnectorProperties("10.0.0.6", 8880, "igor", "Test1234");
		AdminClient adminClient = AdminClientConnectorProperties.createAdminClient(properties);
		amClient = new AppManagementClient(adminClient);
	}

	@Test(expected = RuntimeException.class)
	public void startNonExistent() {
		amClient.startApplication("non_existent_app");
	}

	@Test
	public void startApplication() {
		amClient.installApplication(EAR_FILE, false, APP_NAME);
		amClient.startApplication(APP_NAME);
	}
	
	@Test
	public void stopApplication() {
		amClient.stopApplication(APP_NAME);
	}

	@Test
	public void installApplication() {
		amClient.uninstallApplication(APP_NAME);
		amClient.installApplication(EAR_FILE, false, APP_NAME);
	}
	
	@Test(expected=RuntimeException.class)
	public void installNonExistent() {
		amClient.installApplication("/non/existent/app", false, null);
	}

	@Test
	public void uninstallApplication() {
		amClient.uninstallApplication(APP_NAME);
	}

	@Test(expected=RuntimeException.class)
	public void uninstallNonExistentApplication() {
		String appName = "non_existent_app";
		amClient.uninstallApplication(appName);
	}
	
	@Test
	public void isAppReady() {
		boolean isReady = amClient.isAppReady(APP_NAME);
		assertTrue(isReady);
	}

}