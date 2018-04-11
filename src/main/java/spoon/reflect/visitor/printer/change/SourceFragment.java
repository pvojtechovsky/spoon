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

import java.util.ArrayDeque;
import java.util.Deque;

import spoon.SpoonException;
import spoon.reflect.cu.CompilationUnit;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.cu.position.BodyHolderSourcePosition;
import spoon.reflect.cu.position.DeclarationSourcePosition;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.visitor.CtScanner;
import spoon.reflect.visitor.printer.change.SourcePositionUtils.FragmentDescriptor;
import spoon.support.reflect.cu.position.SourcePositionImpl;

/**
 * Represents a part of source code of an {@link CtElement}
 */
public class SourceFragment  {

	private final CtElement element;
	private final SourcePosition sourcePosition;
	private final FragmentType fragmentType;
	private boolean modified = false;
	FragmentDescriptor fragmentDescriptor;
	private SourceFragment nextSibling;
	private SourceFragment firstChild;

	public SourceFragment(CtElement element, SourcePosition sourcePosition) {
		this(element, sourcePosition, FragmentType.MAIN_FRAGMENT);
	}
	public SourceFragment(CtElement element, SourcePosition sourcePosition, FragmentType fragmentType) {
		super();
		this.element = element;
		this.sourcePosition = sourcePosition;
		this.fragmentType = fragmentType;
		if (fragmentType == FragmentType.MAIN_FRAGMENT) {
			createChildFragments();
		}
	}

	public FragmentType getFragmentType() {
		return fragmentType;
	}

	/**
	 * @return offset of first character which belongs to this fragmen
	 */
	public int getStart() {
		switch (fragmentType) {
		case MAIN_FRAGMENT:
			return sourcePosition.getSourceStart();
		case MODIFIERS:
			return ((DeclarationSourcePosition) sourcePosition).getModifierSourceStart();
		case BEFORE_NAME:
			return ((DeclarationSourcePosition) sourcePosition).getModifierSourceEnd() + 1;
		case NAME:
			return ((DeclarationSourcePosition) sourcePosition).getNameStart();
		case AFTER_NAME:
			return ((DeclarationSourcePosition) sourcePosition).getNameEnd() + 1;
		case BODY:
			return ((BodyHolderSourcePosition) sourcePosition).getBodyStart();
		}
		throw new SpoonException("Unsupported fragment type: " + fragmentType);
	}

	/**
	 * @return offset of first next character after this Fragment
	 */
	public int getEnd() {
		switch (fragmentType) {
		case MAIN_FRAGMENT:
			return sourcePosition.getSourceEnd() + 1;
		case MODIFIERS:
			return ((DeclarationSourcePosition) sourcePosition).getModifierSourceEnd() + 1;
		case BEFORE_NAME:
			return ((DeclarationSourcePosition) sourcePosition).getNameStart();
		case NAME:
			return ((DeclarationSourcePosition) sourcePosition).getNameEnd() + 1;
		case AFTER_NAME:
			if (sourcePosition instanceof BodyHolderSourcePosition) {
				return ((BodyHolderSourcePosition) sourcePosition).getBodyStart();
			}
			return sourcePosition.getSourceEnd() + 1;
		case BODY:
			return ((BodyHolderSourcePosition) sourcePosition).getBodyEnd() + 1;
		}
		throw new SpoonException("Unsupported fragment type: " + fragmentType);
	}

	public SourcePosition getSourcePosition() {
		return sourcePosition;
	}

	@Override
	public String toString() {
		CompilationUnit cu = sourcePosition.getCompilationUnit();
		if (cu != null) {
			String src = cu.getOriginalSourceCode();
			if (src != null) {
				return src.substring(getStart(), getEnd());
			}
		}
		return null;
	}

	public boolean isModified() {
		return modified;
	}

	public void setModified(boolean modified) {
		this.modified = modified;
	}

	/**
	 * @return true if position points to same compilation unit (source file) as this SourceFragment
	 */
	private boolean isFromSameSource(SourcePosition position) {
		return sourcePosition.getCompilationUnit().equals(position.getCompilationUnit());
	}

	/**
	 * Builds tree of {@link SourcePosition} elements
	 * @param element the root element of the tree
	 */
	public void addSourceFragments(CtElement element) {
		SourcePosition sp = element.getPosition();
		Deque<SourceFragment> parents = new ArrayDeque<>();
		parents.push(this);
		new CtScanner() {
			int noSource = 0;
			@Override
			protected void enter(CtElement e) {
				SourceFragment newFragment = addChild(parents.peek(), e);
				if (newFragment != null) {
					parents.push(newFragment);
				} else {
					noSource++;
				}
			}
			@Override
			protected void exit(CtElement e) {
				if (noSource == 0) {
					parents.pop();
				} else {
					noSource--;
				}
			}
		}.scan(element);
	}
	private static SourceFragment addChild(SourceFragment thisFragment, CtElement otherElement) {
		SourcePosition otherSourcePosition = otherElement.getPosition();
		if (otherSourcePosition instanceof SourcePositionImpl && otherSourcePosition.getCompilationUnit() != null) {
			SourcePositionImpl childSPI = (SourcePositionImpl) otherSourcePosition;
			if (thisFragment.sourcePosition != childSPI) {
				if (thisFragment.isFromSameSource(otherSourcePosition)) {
					SourceFragment otherFragment = new SourceFragment(otherElement, otherSourcePosition, FragmentType.MAIN_FRAGMENT);
					//parent and child are from the same file. So we can connect their positions into one tree
					CMP cmp = thisFragment.compare(otherFragment);
					if (cmp == CMP.OTHER_IS_CHILD) {
						//child belongs under parent - OK
						thisFragment.addChild(otherFragment);
					} else {
						//the source position of child element is not included in source position of parent element
//						if (otherElement instanceof CtAnnotation<?>) {
//							/*
//							 * it can happen for annotations of type TYPE_USE and FIELD
//							 * In such case the annotation belongs to 2 elements
//							 * And one of them cannot have matching source position - OK
//							 */
//							return null;
//						}
						//something is wrong ...
						SourcePosition.class.getClass();
						throw new SpoonException("TODO");
					}
				} else {
					throw new SpoonException("SourcePosition from unexpected compilation unit: " + otherSourcePosition + " expected is: " + thisFragment.sourcePosition);
				}
			}
			//else these two elements has same instance of SourcePosition.
			//It is probably OK. Do not created new SourceFragment for that
			return null;
		}
		//do not connect that undefined source position
		return null;
	}

	/**
	 * adds `other` {@link SourceFragment} into tree of {@link SourceFragment}s represented by this root element
	 * @param other to be added {@link SourceFragment}
	 * @return new root of the tree of the {@link SourceFragment}s. It can be be this or `other`
	 */
	public SourceFragment add(SourceFragment other) {
		if (this == other) {
			throw new SpoonException("SourceFragment#add must not be called twice for the same SourceFragment");
			//optionally we might accept that and simply return this
		}
		CMP cmp = this.compare(other);
		switch (cmp) {
		case OTHER_IS_AFTER:
			//other is after this
			addNextSibling(other);
			return this;
		case OTHER_IS_BEFORE:
			//other is before this
			other.addNextSibling(this);
			return other;
		case OTHER_IS_CHILD:
			//other is child of this
			addChild(other);
			return this;
		case OTHER_IS_PARENT:
			//other is parent of this
			other.addChild(this);
			//merge siblings of `this` as children and siblings of `other`
			other.mergeSiblingsOfChild(this);
			return other;
		}
		throw new SpoonException("Unexpected compare result: " + cmp);
	}

	private void mergeSiblingsOfChild(SourceFragment other) {
		SourceFragment prevOther = other;
		other = prevOther.getNextSibling();
		while (other != null) {
			CMP cmp = compare(other);
			if (cmp == CMP.OTHER_IS_CHILD) {
				//ok, it is child too. Keep it as sibling of children
				prevOther = other;
				other = other.getNextSibling();
				continue;
			} else if (cmp == CMP.OTHER_IS_AFTER) {
				//the next sibling of child is after `this`
				//disconnect it from prevOther and connect it as sibling of this
				prevOther.nextSibling = null;
				addNextSibling(other);
				//and we are done, because other.nextSibling is already OK
			}
			throw new SpoonException("Unexpected child SourceFragment");
		}
	}

	private void addChild(SourceFragment child) {
		if (firstChild == null) {
			firstChild = child;
		} else {
			firstChild = firstChild.add(child);
		}
	}

	private void addNextSibling(SourceFragment sibling) {
		if (nextSibling == null) {
			nextSibling = sibling;
		} else {
			nextSibling = nextSibling.add(sibling);
		}
	}

	private enum CMP {
		OTHER_IS_BEFORE,
		OTHER_IS_AFTER,
		OTHER_IS_CHILD,
		OTHER_IS_PARENT
	}

	/**
	 * compares this and other
	 * @param other other {@link SourcePosition}
	 * @return CMP
	 * throws {@link SpoonException} if intervals overlap or start/end is negative
	 */
	private CMP compare(SourceFragment other) {
		if (other == this) {
			throw new SpoonException("SourcePositionImpl#addNextSibling must not be called twice for the same SourcePosition");
		}
		if (getEnd() <= other.getStart()) {
			//other is after this
			return CMP.OTHER_IS_AFTER;
		}
		if (other.getEnd() <= getStart()) {
			//other is before this
			return CMP.OTHER_IS_BEFORE;
		}
		if (getStart() <= other.getStart() && getEnd() >= other.getEnd()) {
			//other is child of this
			return CMP.OTHER_IS_CHILD;
		}
		if (getStart() >= other.getStart() && getEnd() <= other.getEnd()) {
			//other is parent of this
			return CMP.OTHER_IS_PARENT;
		}
		//the fragments overlap - it is not allowed
		throw new SpoonException("Cannot compare this: [" + getStart() + ", " + getEnd() + "] with other: [\"" + other.getStart() + "\", \"" + other.getEnd() + "\"]");
	}

	private void createChildFragments() {
		if (sourcePosition instanceof DeclarationSourcePosition) {
			DeclarationSourcePosition dsp = (DeclarationSourcePosition) sourcePosition;
			int endOfLastFragment = dsp.getSourceStart();
			if (endOfLastFragment < dsp.getModifierSourceStart()) {
				throw new SpoonException("DeclarationSourcePosition#sourceStart < modifierSourceStart for: " + sourcePosition);
			}
			addChild(new SourceFragment(element, sourcePosition, FragmentType.MODIFIERS));
			if (endOfLastFragment < dsp.getNameStart()) {
				addChild(new SourceFragment(element, sourcePosition, FragmentType.BEFORE_NAME));
			}
			addChild(new SourceFragment(element, sourcePosition, FragmentType.NAME));
			if (dsp instanceof BodyHolderSourcePosition) {
				BodyHolderSourcePosition bhsp = (BodyHolderSourcePosition) dsp;
				if (endOfLastFragment < bhsp.getBodyStart()) {
					addChild(new SourceFragment(element, sourcePosition, FragmentType.AFTER_NAME));
				}
				SourceFragment bodyFragment = new SourceFragment(element, sourcePosition, FragmentType.BODY);
				addChild(bodyFragment);
				if (bodyFragment.getEnd() != bhsp.getBodyEnd() + 1) {
					throw new SpoonException("Last bodyEnd is not equal to SourcePosition#sourceEnd: " + sourcePosition);
				}
			} else {
				if (endOfLastFragment < dsp.getSourceEnd() + 1) {
					addChild(new SourceFragment(element, sourcePosition, FragmentType.AFTER_NAME));
				}
			}
		}
	}
	public SourceFragment getNextSibling() {
		return nextSibling;
	}
	public SourceFragment getFirstChild() {
		return firstChild;
	}

	/**
	 * Searches the tree of {@link SourceFragment}s
	 * It searches in siblings and children of this {@link SourceFragment} recursively.
	 *
	 * @param start the start offset of this fragment
	 * @param end the offset of next character after the end of this fragment
	 * @return SourceFragment which represents the root of the CtElement whose sources are in interval [start, end]
	 */
	public SourceFragment getSourceFragmentOf(int start, int end) {
		int myEnd = getEnd();
		if (myEnd <= start) {
			//search in next sibling
			if (nextSibling == null) {
				return null;
			}
			return getRootFragmentOfElement(nextSibling.getSourceFragmentOf(start, end));
		}
		int myStart = getStart();
		if (myStart <= start) {
			if (myEnd >= end) {
				if (myStart == start && myEnd == end) {
					//we have found exact match
					return this;
				}
				//it is the child
				if (firstChild == null) {
					return null;
				}
				return getRootFragmentOfElement(firstChild.getSourceFragmentOf(start, end));
			}
			//start - end overlaps over multiple fragments
			throw new SpoonException("Invalid start/end interval. It overlaps multiple fragments.");
		}
		throw new SpoonException("Invalid start/end interval. It is before this fragment");
	}

	private SourceFragment getRootFragmentOfElement(SourceFragment childFragment) {
		if (childFragment != null && getElement() != null && childFragment.getElement() == getElement()) {
			//child fragment and this fragment have same element. Return this fragment,
			//because we have to return root fragment of CtElement
			return this;
		}
		return childFragment;
	}
	/**
	 * @return {@link CtElement} whose source code is contained in this fragment
	 */
	public CtElement getElement() {
		return element;
	}

	/**
	 * @return direct child {@link SourceFragment} if it has same element like this.
	 * It means that this {@link SourceFragment} knows the source parts of this element
	 * Else it returns null.
	 */
	public SourceFragment getChildFragmentOfSameElement() {
		if (getFirstChild() != null && getFirstChild().getElement() == element) {
			return getFirstChild();
		}
		return null;
	}

	/**
	 * @return direct next sibling {@link SourceFragment} if it has same element like this.
	 * It means that this {@link SourceFragment} knows the next source part of this element
	 * Else it returns null.
	 */
	public SourceFragment getNextFragmentOfSameElement() {
		if (getNextSibling() != null && getNextSibling().getElement() == element) {
			return getNextSibling();
		}
		return null;
	}
}
