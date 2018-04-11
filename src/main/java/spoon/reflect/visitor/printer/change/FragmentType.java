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

import spoon.support.reflect.cu.position.SourcePositionImpl;

/**
 * Represents type of {@link SourceFragment}
 */
public enum FragmentType  {
	/**
	 * a main fragment of {@link SourcePositionImpl}, which represents whole element
	 */
	MAIN_FRAGMENT,
	/**
	 * modifiers and annotations of an Type, Executable or Variable. {@link DeclarationSourcePosition#getModifierSourceStart()}, {@link DeclarationSourcePosition#getModifierSourceEnd()}
	 */
	MODIFIERS,
	/**
	 * a part between {@link #MODIFIERS} and {@link #NAME}
	 */
	BEFORE_NAME,
	/**
	 * name of an Type, Executable or a Variable. {@link DeclarationSourcePosition#getNameStart()}, {@link DeclarationSourcePosition#getNameEnd()}
	 */
	NAME,
	/**
	 * a part between {@link FragmentType#NAME} and {@link FragmentType#BODY}
	 */
	AFTER_NAME,
	/**
	 * body of an Type or an Executable. {@link BodyHolderSourcePosition#getBodyStart()}, {@link BodyHolderSourcePosition#getBodyEnd()}
	 */
	BODY
}
