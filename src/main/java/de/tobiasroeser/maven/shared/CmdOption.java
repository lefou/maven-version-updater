package de.tobiasroeser.maven.shared;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.text.MessageFormat;

@Retention(RetentionPolicy.RUNTIME)
@Target( { ElementType.FIELD, ElementType.METHOD })
@Documented
public @interface CmdOption {

	String longName() default "";

	String shortName() default "";

	/**
	 * The description of the option. You can use placeholders for parameters
	 * like in {@link MessageFormat#format(String, Object...) and variable
	 * substitutes (e.g. ${optA}) to reference other options (e.g. --optA).}.
	 */
	String description() default "";

	String[] args() default {};
}
