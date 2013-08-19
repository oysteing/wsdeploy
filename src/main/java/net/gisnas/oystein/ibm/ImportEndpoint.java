package net.gisnas.oystein.ibm;

public class ImportEndpoint {

	public String importName;
	public String endpointUrl;

	@Override
	public String toString() {
		return importName + "=" + endpointUrl;
	}

}
