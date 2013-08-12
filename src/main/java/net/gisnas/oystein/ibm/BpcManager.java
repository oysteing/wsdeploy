package net.gisnas.oystein.ibm;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ibm.websphere.management.AdminClient;
import com.ibm.websphere.management.configservice.ConfigServiceHelper;
import com.ibm.websphere.management.configservice.ConfigServiceProxy;
import com.ibm.websphere.management.configservice.SystemAttributes;
import com.ibm.websphere.management.exception.ConfigServiceException;
import com.ibm.websphere.management.exception.ConnectorException;

/**
 * Management client for WebSphere applications
 */
public class BpcManager {

	private static final Logger logger = LoggerFactory.getLogger(BpcManager.class);

	private AdminClient adminClient;
	private ConfigServiceProxy configService;

	public BpcManager(AdminClient adminClient) {
		this.adminClient = adminClient;
		try {
			configService = new ConfigServiceProxy(adminClient);
		} catch (InstanceNotFoundException | ConnectorException e) {
			throw new RuntimeException("Unable to connect to deployment manager's ConfigService", e);
		}
	}

	/**
	 * Forcibly stop process templates belonging to an application
	 * 
	 * Will delete all process instances for the process template
	 * 
	 * Requires administrative role Administrator
	 */
	public void stopProcessTemplates(String appName) {
		logger.info("Deleting all process instances for application {}", appName);
		AttributeList deploymentTarget = uniqueDeploymentTarget(appName);
		List<AttributeList> deploymentTargetNodes = getDeploymentNodes(deploymentTarget);
		Set<ObjectName> mBeans = getMbeans("InternalProcessContainer", deploymentTargetNodes);
		logger.debug("Found MBeans: {}", mBeans);
		Set<ProcessTemplate> templates = getProcessTemplates(appName);
		for (ProcessTemplate processTemplate : templates) {
			for (ObjectName mBean : mBeans) {
				stopProcessTemplateAndDeleteProcessesForced(mBean, processTemplate.getTemplateName(), processTemplate.getValidFrom(), appName);
			}
			setConfigStateStopped(processTemplate);
		}
	}

	private Set<ObjectName> getMbeans(String type, List<AttributeList> deploymentTargetNodes) {
		Set<ObjectName> result = new HashSet<>();
		try {
			for (AttributeList deploymentTargetNode : deploymentTargetNodes) {
				String node = (String) ConfigServiceHelper.getAttributeValue(deploymentTargetNode, "nodeName");
				String deploymentTargetType = (String) ConfigServiceHelper.getAttributeValue(deploymentTargetNode, SystemAttributes._WEBSPHERE_CONFIG_DATA_TYPE);
				String process;
				// TODO Improve mbean algorithm
				// TODO Replace deploymentTargetNodes with deploymentTargetServers
				if (deploymentTargetType.equals("ClusterMember")) {
					process = (String) ConfigServiceHelper.getAttributeValue(deploymentTargetNode, "memberName");
				} else {
					process = (String) ConfigServiceHelper.getAttributeValue(deploymentTargetNode, "name");
				}
				ObjectName query = new ObjectName("WebSphere:type=" + type + ",node=" + node + ",process=" + process + ",*");
				logger.debug("Querying MBeans: {}", query);
				Set<?> mBeans = adminClient.queryNames(query, null);
				if (mBeans.size() != 1) {
					throw new RuntimeException("Expected 1 MBean, found " + mBeans.size());
				}
				result.add((ObjectName) mBeans.iterator().next());
			}
		} catch (AttributeNotFoundException | ConnectorException | MalformedObjectNameException e) {
			throw new RuntimeException("An error occured while looking up MBeans", e);
		}
		return result;
	}

	public AttributeList uniqueDeploymentTarget(String appName) {
		try {
			ObjectName[] result = configService.resolve(null, "Deployment=" + appName);
			if (result.length != 1) {
				throw new RuntimeException("Expected one config object for Deployment " + appName + ", found " + result.length);
			}
			ObjectName deployment = result[0];
			List<?> targets = (List<?>) configService.getAttribute(null, deployment, "deploymentTargets");
			if (targets.size() != 1) {
				throw new RuntimeException("Expected one deployment target for application " + appName + ", found " + targets.size());
			}
			AttributeList target = (AttributeList) targets.get(0);
			return target;
		} catch (ConfigServiceException | ConnectorException e) {
			throw new RuntimeException("An error occured while querying application configratuion", e);
		}
	}

	private List<AttributeList> getDeploymentNodes(AttributeList deploymentTarget) {
		logger.debug("Resolving deployment nodes for deploymentTarget {}", deploymentTarget);
		ArrayList<AttributeList> result = new ArrayList<>();
		try {
			String deploymentTargetType = (String) ConfigServiceHelper.getAttributeValue(deploymentTarget, SystemAttributes._WEBSPHERE_CONFIG_DATA_TYPE);
			if (deploymentTargetType.equals("ClusteredTarget")) {
				logger.debug("Deployment target is a cluster, resolving nodes from cluster members");
				String clusterName = (String) ConfigServiceHelper.getAttributeValue(deploymentTarget, "name");
				ObjectName[] clusterResult = configService.resolve(null, "ServerCluster=" + clusterName);
				ObjectName[] clusterMembers = configService.resolve(null, clusterResult[0], "ClusterMember");
				for (ObjectName clusterMember : clusterMembers) {
					AttributeList cmAttributes = configService.getAttributes(null, clusterMember, null, false);
					logger.debug("Found cluster member: {}", cmAttributes);
					result.add(cmAttributes);
				}
			} else {
				result.add(deploymentTarget);
			}
		} catch (AttributeNotFoundException e) {
			throw new RuntimeException("Did not find expected config attribute", e);
		} catch (ConfigServiceException | ConnectorException e) {
			throw new RuntimeException("An error occured in the communcation with the deployment manager", e);
		}
		return result;
	}

	public Set<ProcessTemplate> getProcessTemplates(String applicationName) {
		try {
			ObjectName[] allTemplates = configService.resolve(null, "ProcessComponent");
			Set<ProcessTemplate> templates = new HashSet<>();
			for (ObjectName template : allTemplates) {
				logger.trace("Found ProcessComponent {}", template);
				String dataId = template.getKeyProperty(SystemAttributes._WEBSPHERE_CONFIG_DATA_ID);
				Matcher matcher = Pattern.compile(".*/([^/|]*)|").matcher(dataId);
				matcher.find();
				String appName = matcher.group(1);
				if (applicationName.equals(appName)) {
					String templateName = (String) configService.getAttribute(null, template, "name");
					Date validFrom = new Date((long) configService.getAttribute(null, template, "validFrom"));
					logger.debug("Found process template {} in app {}, valid from {}", templateName, appName, validFrom);
					templates.add(new ProcessTemplate(templateName, validFrom, template));
				}
			}
			return templates;
		} catch (ConnectorException | ConfigServiceException e) {
			throw new RuntimeException("Unable to query process templates", e);
		}
	}

	public void stopProcessTemplateAndDeleteProcessesForced(ObjectName mBean, String templateName, Date validFrom, String appName) {
		try {
			logger.debug("Deleting instances and stopping template {}", templateName);
			adminClient.invoke(mBean, "stopProcessTemplateAndDeleteInstancesForced", new Object[] { templateName, validFrom.getTime(), appName }, new String[] {
					"java.lang.String", "java.lang.Long", "java.lang.String" });
		} catch (InstanceNotFoundException | MBeanException | ReflectionException | ConnectorException e) {
			throw new RuntimeException("Unable to invoke stopProcessTemplateAndDeleteInstancesForced on MBean ProcessContainer", e);
		}
	}

	private void setConfigStateStopped(ProcessTemplate processTemplate) {
		try {
			logger.debug("Setting state STOP for process template {}", processTemplate);
			AttributeList attributeList = new AttributeList();
			attributeList.add(new Attribute("initialState", "STOP"));
			configService.createConfigData(null, processTemplate.getObjectName(), "stateManagement", "StateManageable", attributeList);
		} catch (ConfigServiceException | ConnectorException e) {
			throw new RuntimeException("Unable to change config state with to deployment manager's ConfigService", e);
		}
	}

}