package net.gisnas.oystein.ibm;

import java.util.Date;

import javax.management.ObjectName;

public class ProcessTemplate {

	private String templateName;
	private Date validFrom;
	private ObjectName ObjectName;

	public ProcessTemplate(String templateName, Date validFrom, ObjectName objectName) {
		this.templateName = templateName;
		this.validFrom = validFrom;
		this.ObjectName = objectName;
	}

	public String getTemplateName() {
		return templateName;
	}

	public Date getValidFrom() {
		return validFrom;
	}

	public ObjectName getObjectName() {
		return ObjectName;
	}

	@Override
	public String toString() {
		return templateName + " (valid from " + validFrom + ")";
	}
}
