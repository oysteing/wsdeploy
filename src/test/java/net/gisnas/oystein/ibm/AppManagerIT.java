package net.gisnas.oystein.ibm;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.ibm.websphere.management.AdminClient;

public class AppManagerIT {

	private static final File TRUST_STORE = new File("src/test/resources/trustStore.jks");
	private static final File EAR_FILE = new File("src/test/resources/echoear-0.0.1-SNAPSHOT.ear");
	private static final String APP_NAME = "echoear";

	AppManager am;

	@Before
	public void setUp() {
		AdminClientConnectorProperties properties = new AdminClientConnectorProperties("10.0.0.6", 8880, "igor", "Test1234", TRUST_STORE);
		AdminClient adminClient = AdminClientConnectorProperties.createAdminClient(properties);
		am = new AppManager(adminClient);
	}

	@Test
	public void nonExistentNotStarted() {
		assertFalse(am.isStarted("non_existent_app"));
	}

	@Test
	public void isStarted() {
		am.deploy(EAR_FILE);
		assertTrue(am.isStarted(APP_NAME));
	}

	@Test(expected=RuntimeException.class)
	public void startNonExistent() {
		am.startApp("non_existent_app");
	}

	@Test
	public void startAlreadyRunning() {
		am.deploy(EAR_FILE);
		am.startApp(APP_NAME);
		assertTrue(am.isStarted(APP_NAME));
	}

	@Test
	public void startApp() {
		am.deploy(EAR_FILE);
		am.stopApp(APP_NAME);
		am.startApp(APP_NAME);
		assertTrue(am.isStarted(APP_NAME));
	}

	@Test
	public void stopNonExistent() {
		am.stopApp("non_existent_app");
		Assert.assertFalse(am.isStarted("non_existent_app"));
	}

	@Test
	public void stopApp() {
		am.deploy(EAR_FILE);
		am.stopApp(APP_NAME);
		assertFalse(am.isStarted(APP_NAME));
	}

	@Test
	public void deploy() {
		am.undeploy(APP_NAME);
		am.deploy(EAR_FILE);
	}

	@Test
	public void undeployNonExistent() {
		am.undeploy("non_existent_app");
	}
	
	@Test
	public void undeploy() {
		am.deploy(EAR_FILE);
		am.undeploy(APP_NAME);
	}
}
