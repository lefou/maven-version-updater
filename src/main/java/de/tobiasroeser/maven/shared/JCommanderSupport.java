package de.tobiasroeser.maven.shared;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class JCommanderSupport {

	public static Map<String, String> toMap(List<String> list) {
		if (list.size() % 2 != 0) {
			throw new IllegalArgumentException("List must contain an even number of elements.");
		}
		HashMap<String, String> map = new HashMap<String, String>();
		Iterator<String> it = list.iterator();
		while (it.hasNext()) {
			// we assume, the count of elements is always a multiple of 2
			map.put(it.next(), it.next());
		}
		return map;
	}

}
