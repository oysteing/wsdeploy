package net.gisnas.oystein.ibm;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.w3c.dom.Document;

public class ImportSet {

	private Map<String, Document> name2Document;
	private Map<String, Document> entryName2Document;

	public ImportSet() {
		name2Document = new HashMap<>();
		entryName2Document = new HashMap<>();
	}

	public void put(String name, String entryName, Document doc) {
		name2Document.put(name, doc);
		entryName2Document.put(entryName, doc);
	}

	public Set<String> nameKeySet() {
		return name2Document.keySet();
	}

	public Document getByName(String importName) {
		return name2Document.get(importName);
	}

	public Set<String> entryNameKeySet() {
		return entryName2Document.keySet();
	}

	public Document getByEntryName(String entryName) {
		return entryName2Document.get(entryName);
	}

	@Override
	public String toString() {
		return name2Document.toString() + entryName2Document.toString();
	}

}
