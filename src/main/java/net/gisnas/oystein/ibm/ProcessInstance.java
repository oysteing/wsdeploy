package net.gisnas.oystein.ibm;

import java.util.List;

public class ProcessInstance {

	/**
	 * @param columnList
	 *            List containing the following values: piid, state
	 */
	public ProcessInstance(List<Object> columnList) {
		piid = (String) columnList.get(0);
		state = (String) columnList.get(1);
	}

	private String piid;
	private String state;

	public String getPiid() {
		return piid;
	}

	public String getState() {
		return state;
	}

	@Override
	public String toString() {
		return piid + " (" + state + ")";
	}

}
