package net.gisnas.oystein.ibm;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

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
	public void deleteAllProcessInstances(String appName) {
		logger.debug("Deleting all process instances for application {}", appName);
		List<String> piids = queryProcessInstances(appName);
		deleteProcessInstances(piids);
	}

	private List<String> queryProcessInstances(String appName) {
		try {
			QueryResultSetType result = bfm.query("DISTINCT PROCESS_INSTANCE.PIID", String.format("PROCESS_TEMPLATE.APPLICATION_NAME = '%s'", appName), null,
					null, null, null);
			logger.debug("Found {} process instances for application {}", result.getSize(), appName);
			List<String> piidList = new ArrayList<>();
			for (QueryResultRowType row : result.getResult().getQueryResultRow()) {
				String piid = (String) row.getValue().get(0);
				logger.trace("Adding process instance with piid {} to query result", piid);
				piidList.add(piid);
			}
			return piidList;
		} catch (ProcessFaultMsg e) {
			throw new RuntimeException("Unable to query process instances", e);
		}
	}

	private void deleteProcessInstances(List<String> piids) {
		for (String piid : piids) {
			try {
				logger.debug("Deleting process instance with piid {}", piid);
				bfm.delete(piid);
			} catch (ProcessFaultMsg e) {
				throw new RuntimeException("Unable to delete process instance " + piid, e);
			}
		}
	}
}
