/**
 * Copyright (C) 2006-2017 INRIA and contributors
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
package spoon.reflect.visitor.printer.change;

import spoon.compiler.Environment;
import spoon.reflect.visitor.PrinterHelper;

/**
 * Extension of {@link PrinterHelper}, which allows direct printing of source fragments
 */
class DirectPrinterHelper extends PrinterHelper {

	DirectPrinterHelper(Environment env) {
		super(env);
	}

	/**
	 * Prints `str` directly into output buffer ignoring any Environment rules.
	 * @param str to be printed string
	 */
	void directPrint(String str) {
		autoWriteTabs();
		sbf.append(str);
	}

	public void setShouldWriteTabs(boolean shouldWriteTabs) {
		this.shouldWriteTabs = shouldWriteTabs;
	}
}
