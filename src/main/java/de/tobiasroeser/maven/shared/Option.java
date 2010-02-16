package de.tobiasroeser.maven.shared;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class Option implements Comparable<Option> {

	private final String longOption;
	private final String shortOption;
	private final String description;
	private final String[] args;

	public Option(String longOption, String shortOption, String description,
			String... args) {
		this.longOption = longOption;
		this.shortOption = shortOption;
		if (longOption == null && shortOption == null) {
			throw new IllegalArgumentException(
					"Must at least give one option type (long or short).");
		}
		this.description = description;
		this.args = args;
	}

	/**
	 * Scan the string for occurrences of this option and return the position of
	 * the option, if found.
	 * 
	 * @param params
	 *            The list of parameters.
	 * @return The position of the option in the given list <code>params</code>
	 *         or <code>-1</code> if not found.
	 */
	public int scanPosition(final List<String> params) {
		int index = -1;
		if (longOption != null) {
			index = params.indexOf("--" + longOption);
		}
		if (index == -1 && shortOption != null) {
			index = params.indexOf("-" + shortOption);
		}
		return index;
	}

	public String getLongOption() {
		return longOption;
	}

	public String getShortOption() {
		return shortOption;
	}

	public String getDescription() {
		return description;
	}

	public String[] getArgs() {
		return args;
	}

	public int getArgCount() {
		if (args != null) {
			return args.length;
		} else {
			return 0;
		}
	}

	public String formatOptionString() {

		String formatted = null;
		if (longOption != null && shortOption != null) {
			formatted = "--" + longOption + ", -" + shortOption;
		} else if (longOption != null) {
			formatted = "--" + longOption;
		} else {
			formatted = "-" + shortOption;
		}

		String arguments = null;
		if (args != null && args.length > 0) {
			for (String arg : args) {
				if (arguments == null) {
					arguments = arg;
				} else {
					arguments += " " + arg;
				}
			}
			return formatted + " " + arguments;
		} else {
			return formatted;
		}
	}

	public int compareTo(Option o) {
		String left = getLongOption() != null ? getLongOption()
				: getShortOption();
		String right = o.getLongOption() != null ? o.getLongOption() : o
				.getShortOption();

		int comp = left.toLowerCase().compareTo(right.toLowerCase());
		return comp != 0 ? comp : left.compareTo(right);
	}

	public static String formatOptions(List<Option> options, String prefix,
			boolean sorted) {
		LinkedList<String[]> optionsToFormat = new LinkedList<String[]>();

		List<Option> sortedOptions = sorted ? new LinkedList<Option>(options)
				: options;
		if (sorted) {
			Collections.sort(sortedOptions);
		}

		for (Option option : sortedOptions) {
			optionsToFormat.add(new String[] { option.formatOptionString(),
					option.getDescription() });
		}

		int firstColSize = 8;
		for (String[] strings : optionsToFormat) {
			if (strings.length > 0) {
				firstColSize = Math.max(firstColSize, strings[0].length());
			}
		}
		firstColSize += 2;
		String optionsString = "";
		optionsString += prefix != null ? prefix : "Options:";
		for (String[] strings : optionsToFormat) {
			if (strings.length > 0) {
				optionsString += "\n" + strings[0];
			}
			if (strings.length > 1) {
				for (int count = firstColSize - strings[0].length(); count > 0; --count) {
					optionsString += " ";
				}
				optionsString += strings[1];
			}
		}

		return optionsString;
	}

	@Override
	public String toString() {
		return getLongOption() != null ? "--" + getLongOption() : "-"
				+ getShortOption();
	}

	private static Option convertToOption(final String fieldOrMethodName,
			String longName, String shortName, String description, String[] args) {
		if (longName.equals("") && shortName.equals("")) {
			longName = fieldOrMethodName;
		}
		if (!description.equals("") && args.length > 0) {
			description = MessageFormat.format(description, (Object[]) args);
		}
		// TODO: replace other options in description string
		return new Option(longName.equals("") ? null : longName, shortName
				.equals("") ? null : shortName, description, args);
	}

	public static List<Option> scanCmdOpions(Class<?> configClass) {
		LinkedList<Option> options = new LinkedList<Option>();

		for (Field field : configClass.getFields()) {
			CmdOption anno = field.getAnnotation(CmdOption.class);
			if (anno != null) {
				options.add(convertToOption(field.getName(), anno.longName(),
						anno.shortName(), anno.description(), anno.args()));
			}
		}

		for(Method method : configClass.getMethods()) {
			CmdOption anno = method.getAnnotation(CmdOption.class);
			if(anno != null) {
				options.add(convertToOption(method.getName(), anno.longName(),
						anno.shortName(), anno.description(), anno.args()));
			}
		}
		
		return options;
	}
}
