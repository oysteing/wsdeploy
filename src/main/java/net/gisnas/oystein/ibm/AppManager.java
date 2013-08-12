package net.gisnas.oystein.ibm;

import java.io.File;
import java.util.Set;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ibm.websphere.management.AdminClient;
import com.ibm.websphere.management.application.client.AppDeploymentException;
import com.ibm.websphere.management.exception.ConnectorException;
import com.ibm.ws.management.application.client.AppInstallHelper;

/**
 * Management client for WebSphere applications
 */
public class AppManager {

	private static final Logger logger = LoggerFactory.getLogger(AppManager.class);

	private AppManagementClient am;
	private AdminClient adminClient;

	public AppManager(AdminClient adminClient) {
		am = new AppManagementClient(adminClient);
		this.adminClient = adminClient;
		// Test connection (for missing permissions for example)
		try {
			am.checkIfAppExists("non_existent_app");
		} catch (RuntimeException e) {
			throw new RuntimeException("Failed to connect to deployment manager. Check username/password and permissions (failed operation which required Monitor role)", e);
		}
	}

	/**
	 * Checks if application is started on all nodes
	 * 
	 * @param appName
	 * @return true if application is started on all nodes
	 */
	public boolean isStarted(String appName) {
		logger.debug("Checking if application {} is started", appName);
		ObjectName query;
		try {
			query = new ObjectName("WebSphere:type=Application,name=" + appName + ",*");
		} catch (MalformedObjectNameException e) {
			throw new RuntimeException("Could not query MBean", e);
		}
		try {
			Set<?> result = adminClient.queryNames(query, null);
			if (result.size() == 0) {
				logger.debug("Application {} is not started", appName);
				return false;
			}
			Set<String> targets = am.getAppAssociation(appName);
			logger.debug("Found deployment targets for application {}: {}", appName, targets);
			if (result.size() == targets.size()) {
				logger.debug("Application {} is started", appName);
				return true;
			} else if (result.size() < targets.size()) {
				logger.debug("Application {} is started on {} og {} deployment targets", appName, result.size(), targets.size());
				return false;
			} else {
				logger.warn("Got unexpected result when querying application {} to see if it's started (started on {} targets out of {}", appName, result.size(), targets.size());
				throw new RuntimeException("Got unexpected result when querying application to see if it's started");
			}
		} catch (ConnectorException e) {
			throw new RuntimeException("An error occured in the communication with the deployment manager", e);
		}
	}

	/**
	 * Start application on all deployment targets, if not already running
	 * 
	 * Application name is extracted from display-name in earFile's application.xml
	 * 
	 * Requires administrator role Operator, Deployer or Administrator 
	 * 
	 * @param earFile
	 */
	public void startApp(File earFile) {
		String appName = extractAppName(earFile);
		startApp(appName);
	}

	/**
	 * Start application on all deployment targets, if not already running
	 * 
	 * Requires administrator role Operator, Deployer or Administrator 
	 * 
	 * @param appName
	 */
	public void startApp(String appName) {
		logger.debug("Attempting to start application {}", appName);
		if (isStarted(appName)) {
			logger.debug("Application {} is already started. Doing nothing.", appName);
			return;
		}
		logger.debug("Starting application {}", appName);
		am.startApplication(appName);
		logger.info("Application {} started", appName);
	}

	/**
	 * Stop application on all deployment targets, if not already stopped
	 *
	 * Application name is extracted from display-name in earFile's application.xml
	 * 
	 * Requires administrator role Operator, Deployer or Administrator 
	 * 
	 * @param earFile
	 */
	public void stopApp(File earFile) {
		stopApp(extractAppName(earFile));
	}

	/**
	 * Stop application on all deployment targets, if not already stopped
	 * 
	 * Requires administrator role Operator, Deployer or Administrator 
	 * 
	 * @param appName
	 */
	public void stopApp(String appName) {
		logger.debug("Attempting to stop application {}", appName);
		if (!isStarted(appName)) {
			logger.debug("Application {} is not running. Doing nothing.", appName);
			return;
		}
		logger.debug("Stopping application {}", appName);
		am.stopApplication(appName);
		logger.info("Application {} stopped", appName);
	}

	/**
	 * Deploy application
	 * -Install application
	 * -Wait for distribution
	 * -Start application
	 *  
	 * Will update if the application is already installed
	 * 
	 * Requires administrator role Deployer or Administrator 
	 * 
	 * @param earFile
	 */
	public void deploy(File earFile) {
		deploy(earFile, null, null);
	}

	/**
	 * Deploy application
	 * -Install application
	 * -Wait for distribution
	 * -Start application
	 * 
	 * Will update if the application is already installed
	 * 
	 * Requires administrator role Deployer or Administrator 
	 * 
	 * @param earFile
	 * @param appName Application name. If not set, uses display-name in application.xml.
	 * @param cluster Deploy to this cluster. Must be set if more than one clusters/servers.
	 */
	public void deploy(File earFile, String appName, String cluster) {
		if (appName == null) {
			appName = extractAppName(earFile);
		}
		logger.debug("Deploying {}", appName);
		boolean appExists = am.checkIfAppExists(appName);
		if (cluster == null) {
			am.installApplication(earFile.getPath(), appExists, appName);
		} else {
			ObjectName clusterON = am.lookupCluster(cluster);
			String clusterId = AppManagementClient.createClusterString(clusterON);
			am.installApplication(earFile.getPath(), appExists, appName, clusterId);
		}
		if (!isStarted(appName)) {
			int pollInterval = 1000;
			while (!am.isAppReady(appName)) {
				try {
					logger.debug("App distribution not ready, waiting {} ms", pollInterval);
					Thread.sleep(pollInterval);
				} catch (InterruptedException e) {
					logger.debug("Interrupted when waiting from app ready. Continuing...");
				}
			};
			am.startApplication(appName);
		}
		logger.info("Application {} deployed successfully", appName);
	}

	/**
	 * Undeploy application
	 * 
	 * Application name is extracted from display-name in earFile's application.xml
	 * 
	 * Requires administrator role Configurator, Deployer or Administrator 
	 * 
	 * @param earFile
	 */
	public void uninstallApplication(File earFile) {
		undeploy(extractAppName(earFile));
	}

	/**
	 * Undeploy application
	 * 
	 * Requires administrator role Configurator, Deployer or Administrator 
	 * 
	 * @param appName
	 */
	public void undeploy(String appName) {
		logger.debug("Undeploying {}", appName);
		boolean appExists = am.checkIfAppExists(appName);
		if (appExists) {
			am.uninstallApplication(appName);
		}
		logger.info("Application {} undeployed successfully", appName);
	}

	/**
	 * Extract application name from display-name in EAR file's application.xml
	 * 
	 * @param earFile
	 * @return application name
	 */
	public static String extractAppName(File earFile) {
		try {
			String appName = AppInstallHelper.getAppDisplayName(AppInstallHelper.getEarFile(earFile.getPath(), false, false, null), null);
			return appName;
		} catch (AppDeploymentException e) {
			throw new RuntimeException("An error occured while reading EAR file " + earFile, e);
		}
	}

}