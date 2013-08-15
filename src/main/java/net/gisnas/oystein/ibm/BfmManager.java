package net.gisnas.oystein.ibm;

import java.math.BigInteger;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.ws.BindingProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ibm.xmlns.prod.websphere.bpc_common.types._7.QueryResultRowType;
import com.ibm.xmlns.prod.websphere.bpc_common.types._7.QueryResultSetType;
import com.ibm.xmlns.prod.websphere.business_process.services._7_0.binding.BFMJAXWSPortType;
import com.ibm.xmlns.prod.websphere.business_process.services._7_0.binding.BFMJAXWSService;
import com.ibm.xmlns.prod.websphere.business_process.services._7_0.binding.ProcessFaultMsg;
import com.sun.xml.wss.XWSSConstants;

public class BfmManager {

	private static final Logger logger = LoggerFactory.getLogger(BfmManager.class);
	private static final URL wsdlLocation = BFMJAXWSService.class.getResource("/bfmjaxws.war/WEB-INF/wsdl/com/ibm/bpe/api/jaxws/BFMJAXWSService.wsdl");
	private static final Set<String> deletableProcessStates = new HashSet<>();
	
	{
		deletableProcessStates.add("STATE_FINISHED");
		deletableProcessStates.add("STATE_TERMINATED");
		deletableProcessStates.add("STATE_COMPENSATED");
		deletableProcessStates.add("STATE_FAILED");
	}

	private BFMJAXWSPortType bfm;

	/**
	 * If the endpoint is secured with SSL/TLS, make sure the CA certificate is
	 * added to the JVMs trust store, or set system property
	 * javax.net.ssl.trustStore to the correct trust store
	 * 
	 * @param bfmJaxwsEndpoint
	 * @param username
	 * @param password
	 */
	public BfmManager(String bfmJaxwsEndpoint, String username, String password) {
		logger.debug("Preparing secured SOAP/HTTP connection (username {}) to Business Flow Manager at {}", username, bfmJaxwsEndpoint);
		bfm = new BFMJAXWSService(wsdlLocation).getBFMJAXWSPort();
		((BindingProvider) bfm).getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, bfmJaxwsEndpoint);
		((BindingProvider) bfm).getRequestContext().put(XWSSConstants.USERNAME_PROPERTY, username);
		((BindingProvider) bfm).getRequestContext().put(XWSSConstants.PASSWORD_PROPERTY, password);
	}

	/**
	 * Terminate and delete all process instances of process templates in an
	 * application
	 * 
	 * Requires process administrator role
	 * 
	 * @param appName
	 */
	public void forceDeleteAllProcessInstances(String appName) {
		logger.debug("Deleting all process instances for application {}", appName);
		List<ProcessInstance> processInstances = queryProcessInstances(appName);
		terminateProcessInstances(processInstances);
		deleteProcessInstances(processInstances);
	}

	private void terminateProcessInstances(List<ProcessInstance> processInstances) {
		for (ProcessInstance processInstance : processInstances) {
			if (!deletableProcessStates.contains(processInstance.getState())) {
				logger.debug("Terminating process instance {}", processInstance);
				try {
					bfm.forceTerminate(processInstance.getPiid(), BigInteger.valueOf(0));
				} catch (ProcessFaultMsg e) {
					throw new RuntimeException("Unable to terminate process instance " + processInstance, e);
				}
			}
		}
	}

	private List<ProcessInstance> queryProcessInstances(String appName) {
		try {
			QueryResultSetType result = bfm.query("DISTINCT PROCESS_INSTANCE.PIID, PROCESS_INSTANCE.STATE", String.format("PROCESS_TEMPLATE.APPLICATION_NAME = '%s'", appName), null,
					null, null, null);
			logger.debug("Found {} process instances for application {}", result.getSize(), appName);
			List<ProcessInstance> processList = new ArrayList<>();
			for (QueryResultRowType row : result.getResult().getQueryResultRow()) {
				ProcessInstance processInstance = new ProcessInstance(row.getValue());
				logger.trace("Adding process instance with piid {} to query result", processInstance);
				processList.add(processInstance);
			}
			return processList;
		} catch (ProcessFaultMsg e) {
			throw new RuntimeException("Unable to query process instances", e);
		}
	}

	private void deleteProcessInstances(List<ProcessInstance> processInstances) {
		for (ProcessInstance processInstance : processInstances) {
			try {
				logger.debug("Deleting process instance with piid {}", processInstance);
				bfm.delete(processInstance.getPiid());
			} catch (ProcessFaultMsg e) {
				throw new RuntimeException("Unable to delete process instance " + processInstance, e);
			}
		}
	}
}
