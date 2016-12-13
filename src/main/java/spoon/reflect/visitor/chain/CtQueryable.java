/**
 * Copyright (C) 2006-2016 INRIA and contributors
 * Spoon - http://spoon.gforge.inria.fr/
 *
 * This software is governed by the CeCILL-C License under French law and
 * abiding by the rules of distribution of free software. You can use, modify
 * and/or redistribute the software under the terms of the CeCILL-C license as
 * circulated by CEA, CNRS and INRIA at http://www.cecill.info.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the CeCILL-C License for more details.
 *
 * The fact that you are presently reading this means that you have had
 * knowledge of the CeCILL-C license and that you accept its terms.
 */
package spoon.reflect.visitor.chain;

import spoon.reflect.declaration.CtElement;
import spoon.reflect.visitor.Filter;

/**
 * QueryComposer contains methods, which can be used to create/compose a {@link CtQuery}
 * It is implemented 1) by {@link CtElement} to allow creation new query and its first step
 * 2) by {@link CtQuery} to allow creation of next query step
 */
public interface CtQueryable {

	/**
	 * Appends a queryStep to the query.
	 * When the query is executed then this queryStep, works like this:<br>
	 * It gets two parameters<br>
	 * 1) input element<br>
	 * 2) output {@link Consumer}<br>
	 * The query sends input to the queryStep and the queryStep
	 * sends the result element(s) of this queryStep by calling out output.accept(result)
	 *
	 * @param code
	 * @return the create QueryStep, which is now the last step of the query
	 */
	<T> CtQuery<T> map(CtQueryStep<?, T> queryStep);

	/**
	 * Appends a Function based queryStep to the query.
	 * When the query is executed then this queryStep, works like this:<br>
	 * It gets one input parameter and returns an Object.
	 * The exact behavior depends on type of returned object.
	 * <table>
	 * <tr><td><b>Return type</b><td><b>Behavior</b>
	 * <tr><td>{@link Boolean}<td>Sends input of this step to the next step if returned value is true
	 * <tr><td>{@link Iterable}<td>Sends each item of Iterable to the next step
	 * <tr><td>{@link Object[]}<td>Sends each item of Array to the next step
	 * <tr><td>? extends {@link Object}<td>Sends returned value to the next step
	 * </table><br>
	 *
	 * @param code a Function with one parameter of type I returning value of type R
	 * @return the create QueryStep, which is now the last step of the query
	 */
	<I, R> CtQuery<R> map(CtFunction<I, R> code);

	/**
	 * scan all child elements of an input element.
	 * The child element is sent to next step only if filter.matches(element)==true
	 *
	 * Note: the input element is also checked for match and if true it is sent to output too.
	 * This step never throws {@link ClassCastException}.
	 * The elements which would throw {@link ClassCastException} during {@link Filter#matches(CtElement)}
	 * are understood as not matching.
	 *
	 * @param filter used to filter scanned children elements of AST tree
	 * @return the created QueryStep, which is now the last step of the query
	 */
	<T extends CtElement> CtQuery<T> filterChildren(Filter<T> filter);
}
