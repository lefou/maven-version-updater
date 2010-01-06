/**
 * $Id: VariableExpander.java 122 2008-04-28 16:17:26Z TobiasRoeser $
 * Created on 2008-01-11.
 * 
 * Copyright (C) 2007 - 2008 by Tobias Roeser
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */
package org.jackage.util;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;

public class VariableExpander<S> {

	private final Set<ExpandVar<S, String>> expands = new LinkedHashSet<ExpandVar<S, String>>();

	public VariableExpander() {
	}

	public void addVar(final S var, final String content) {
		addVar(new ExpandVar<S, String>(var, content));
	}

	public void addVar(final ExpandVar<S, String> expander) {
		expands.add(expander);
	}

	public String expand(String string) {
		if (string == null) {
			return "";
		}
		for (final ExpandVar<S, String> var : expands) {
			string = string.replaceAll("\\$\\{" + var.key.toString() + "\\}",
					Matcher.quoteReplacement(var.value.toString()));
		}
		return string;
	}

	public class ExpandVar<K, V> {
		public K key;
		public V value;

		public ExpandVar(final K key, final V value) {
			this.key = key;
			this.value = value;
		}
	}

}
