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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

import spoon.SpoonException;
import spoon.experimental.modelobs.ChangeCollector;
import spoon.reflect.cu.CompilationUnit;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.path.CtRole;
import spoon.reflect.visitor.DefaultJavaPrettyPrinter;
import spoon.reflect.visitor.TokenWriter;
import spoon.support.reflect.cu.position.SourcePositionImpl;

/**
 * source code position helper methods
 */
public abstract class SourcePositionUtils  {

	public static SourceFragment getSourceFragmentOfElement(CtElement element) {
		SourcePosition sp = element.getPosition();
		if (sp.getCompilationUnit() != null) {
			CompilationUnit cu = sp.getCompilationUnit();
			return cu.getSourceFragment(element);
		}
		return null;
	}

	/**
	 * @param changeCollector
	 * @param element
	 * @return SourceFragment which wraps all the source parts of the `element`
	 */
	public static SourceFragment getSourceFragmentsOfElement(ChangeCollector changeCollector, CtElement element) {
		//detect source code fragments of this element
		SourceFragment rootFragmentOfElement = getSourceFragmentOfElement(element);
		if (rootFragmentOfElement == null) {
			//we have no origin sources for this element
			return null;
		}
		//The origin sources of this element are available
		//check if this element was changed
		Set<CtRole> changedRoles = changeCollector.getChanges(element);
		if (changedRoles.isEmpty()) {
			//element was not changed and we know origin sources
			//use origin source instead of printed code
			return rootFragmentOfElement;
		}
		//element is changed. Detect source fragments of this element
		SourceFragment childSourceFragmentsOfSameElement = rootFragmentOfElement.getChildFragmentOfSameElement();
		if (childSourceFragmentsOfSameElement == null || childSourceFragmentsOfSameElement.getNextFragmentOfSameElement() == null) {
			//there is only one source fragment and it is modified.
			//So we cannot use origin sources
			return null;
		}
		/*
		 * there are more fragments. So may be some of them are not modified and we can use origin source to print them
		 * e.g. when only type members of class are modified, we can still print the class header from the origin sources
		 * Mark which fragments contains source code of data from modified roles
		 */
		//detect which roles of this element contains a change
		if (markChangedFragments(element, childSourceFragmentsOfSameElement, changedRoles)) {
			return childSourceFragmentsOfSameElement;
		}
		//this kind of changes is not supported for this element yet. We cannot use origin sources :-(
		return null;
	}

	/**
	 * @param element
	 * @return {@link SourcePosition} if it is not empty and the origin sources are available. Else it returns null
	 */
	private static SourcePosition getNonEmptySourcePosition(CtElement element) {
		if (element != null) {
			SourcePosition sp = element.getPosition();
			if (sp.getSourceStart() >= 0 && sp.getSourceEnd() >= 0 && sp.getCompilationUnit() != null && sp.getCompilationUnit().getOriginalSourceCode() != null) {
				return sp;
			}
		}
		return null;
	}

	/**
	 * Maps {@link FragmentType} to type specific {@link FragmentDescriptor}
	 * depending on element type of the {@link SourceFragment}
	 */
	private static class TypeToFragmentDescriptor {
		Class<? extends CtElement> type;
		Map<FragmentType, FragmentDescriptor> fragmentToRoles = new HashMap<>();
		TypeToFragmentDescriptor(Class<? extends CtElement> type) {
			this.type = type;
		}

		TypeToFragmentDescriptor fragment(FragmentType ft, Consumer<FragmentDescriptorBuilder> initializer) {
			FragmentDescriptor fd = new FragmentDescriptor();
			initializer.accept(new FragmentDescriptorBuilder(fd));
			fragmentToRoles.put(ft, fd);
			return this;
		}

		public boolean matchesElement(CtElement element) {
			return type.isInstance(element);
		}
	}

	enum FragmentKind {
		/**
		 * normal fragment without special handling
		 */
		NORMAL,
		/**
		 * a fragment, which contains a list of elements
		 */
		LIST
	}

	/**
	 * Defines how to handle printing of related fragment
	 */
	static class FragmentDescriptor {
		FragmentKind kind = FragmentKind.NORMAL;
		/**
		 * set of {@link CtRole}s, whose source code is contained in related {@link SourceFragment}.
		 * For example when printing {@link CtClass}, then {@link SourceFragment} with roles=[CtRole.ANNOTATION, CtRole.MODIFIER]
		 * has type {@link FragmentType#MODIFIERS} and contains source code of annotations and modifiers of printed {@link CtClass}
		 */
		Set<CtRole> roles;
		/**
		 * List of predicates, which detects whether linked {@link SourceFragment}
		 * is going to be printed NOW (before the token of {@link TokenWriter} is written)
		 */
		private List<BiPredicate<String, String>> startTokenDetector = new ArrayList<>();
		/**
		 * List of predicates, which detects whether linked {@link SourceFragment}
		 * is going to finish it's printing NOW (after the token of {@link TokenWriter} is written)
		 */
		private List<BiPredicate<String, String>> endTokenDetector = new ArrayList<>();
		/**
		 * Detects whether linked {@link SourceFragment}
		 * is going to be printed NOW (before the CtElement on the role with respect to parent is scanned by {@link DefaultJavaPrettyPrinter})
		 *
		 * For example when printing CtMethod the printing of method modifiers is finished
		 * when CtElement with role {@link CtRole#TYPE} enters the {@link DefaultJavaPrettyPrinter} scanner
		 */
		private Set<CtRole> startScanRole = new HashSet<>();

		/**
		 * initializes fragmentDescriptors of the {@link SourceFragment} and detects whether {@link SourceFragment} is modified
		 * @param fragment
		 * @param changedRoles
		 */
		void applyTo(SourceFragment fragment, Set<CtRole> changedRoles) {
			if (roles != null) {
				for (CtRole ctRole : roles) {
					if (changedRoles.remove(ctRole)) {
						//the role of this fragment is modified -> fragment is modified
						fragment.setModified(true);
						//and continue to remove other roles if they are changed too
						//so at the end we can detect whether all changed roles
						//are covered by this algorithm. If not then change in such role is not supported
						//and standard printing must be used
					}
				}
			}
			fragment.fragmentDescriptor = this;
		}

		/**
		 * Detects whether token triggers start/end usage of {@link SourceFragment}
		 * related to this {@link FragmentDescriptor}
		 *
		 * @param isStart if true then it checks start trigger. if false then it checks end trigger
		 * @param tokenWriterMethodName the name of {@link TokenWriter} method whose token is fired
		 * @param token the value of the {@link TokenWriter} token
		 * @return true this token triggers start/end of usage of {@link SourceFragment}
		 */
		boolean isTriggeredByToken(boolean isStart, String tokenWriterMethodName, String token) {
			for (BiPredicate<String, String> predicate : isStart ? startTokenDetector : endTokenDetector) {
				if (predicate.test(tokenWriterMethodName, token)) {
					return true;
				}
			}
			return false;
		}

		/**
		 * Detects whether {@link DefaultJavaPrettyPrinter} scanning of element on `role`  triggers start usage of {@link SourceFragment}
		 * related to this {@link FragmentDescriptor}
		 *
		 * @param role the role (with respect to parent) of scanned element
		 * @return true is scanning of this element triggers start of usage of {@link SourceFragment}
		 */
		boolean isStartedByScanRole(CtRole role) {
			return startScanRole.contains(role);
		}

		/**
		 * @return {@link CtRole} of the list attribute handled by this fragment
		 */
		public CtRole getListRole() {
			if (kind != FragmentKind.LIST || roles == null || roles.size() != 1) {
				throw new SpoonException("This fragment does not have list role");
			}
			return roles.iterator().next();
		}
	}

	/**
	 * Used to build {@link FragmentDescriptor}s in readable way
	 */
	private static class FragmentDescriptorBuilder {
		FragmentDescriptor descriptor;

		FragmentDescriptorBuilder(FragmentDescriptor descriptor) {
			super();
			this.descriptor = descriptor;
		}

		FragmentDescriptorBuilder role(CtRole... roles) {
			descriptor.roles = new HashSet(Arrays.asList(roles));
			return this;
		}

		FragmentDescriptorBuilder list(CtRole role) {
			if (descriptor.roles != null) {
				throw new SpoonException("Cannot combine #role and #list");
			}
			descriptor.roles = Collections.singleton(role);
			descriptor.kind = FragmentKind.LIST;
			return this;
		}

		FragmentDescriptorBuilder startWhenKeyword(String...tokens) {
			initTokenDetector(descriptor.startTokenDetector, "writeKeyword", tokens);
			return this;
		}
		FragmentDescriptorBuilder endWhenKeyword(String...tokens) {
			initTokenDetector(descriptor.endTokenDetector, "writeKeyword", tokens);
			return this;
		}
		FragmentDescriptorBuilder startWhenSeparator(String...tokens) {
			initTokenDetector(descriptor.startTokenDetector, "writeSeparator", tokens);
			return this;
		}
		FragmentDescriptorBuilder endWhenSeparator(String...tokens) {
			initTokenDetector(descriptor.endTokenDetector, "writeSeparator", tokens);
			return this;
		}
		FragmentDescriptorBuilder startWhenIdentifier(String...tokens) {
			initTokenDetector(descriptor.startTokenDetector, "writeIdentifier", tokens);
			return this;
		}
		FragmentDescriptorBuilder endWhenIdentifier(String...tokens) {
			initTokenDetector(descriptor.endTokenDetector, "writeIdentifier", tokens);
			return this;
		}
		FragmentDescriptorBuilder startWhenScan(CtRole...roles) {
			descriptor.startScanRole.addAll(Arrays.asList(roles));
			return this;
		}

		void initTokenDetector(List<BiPredicate<String, String>> detectors, String tokenWriterMethodName, String...tokens) {
			BiPredicate<String, String> predicate;
			if (tokens.length == 0) {
				predicate = (methodName, token) -> {
					return methodName.equals(tokenWriterMethodName);
				};
			} else if (tokens.length == 1) {
				String expectedToken = tokens[0];
				predicate = (methodName, token) -> {
					return methodName.equals(tokenWriterMethodName) && expectedToken.equals(token);
				};
			} else {
				Set<String> kw = new HashSet<>(Arrays.asList(tokens));
				predicate = (methodName, token) -> {
					return methodName.equals(tokenWriterMethodName) && kw.contains(token);
				};
			}
			detectors.add(predicate);
		}
	}

	private static TypeToFragmentDescriptor type(Class<? extends CtElement> type) {
		return new TypeToFragmentDescriptor(type);
	}

	private static final List<TypeToFragmentDescriptor> descriptors = Arrays.asList(
			//when printing any CtType
			type(CtType.class)
			//then source fragment of type MODIFIERS
			.fragment(FragmentType.MODIFIERS,
					//contains source code of elements on roles ANNOTATION and MODIFIER
					i -> i.role(CtRole.ANNOTATION, CtRole.MODIFIER))
			//and source fragment which is located after modifiers and before the name
			.fragment(FragmentType.BEFORE_NAME,
					//starts to be active when one of these keywords or separator '@' is fired by TokenWriter
					i -> i.startWhenKeyword("class", "enum", "interface", "").startWhenSeparator("@"))
			//and source fragment NAME
			.fragment(FragmentType.NAME,
					//contains source code of attribute value NAME
					//and starts when TokenWriter is going to print any identifier
					//and ends when TokenWriter printed that identifier
					i -> i.role(CtRole.NAME).startWhenIdentifier().endWhenIdentifier())
			//and source fragment located after NAME and before BODY
			.fragment(FragmentType.AFTER_NAME,
					//contains source code of elements on roles SUPER_TYPE, INTERFACE, TYPE_PARAMETER
					i -> i.role(CtRole.SUPER_TYPE, CtRole.INTERFACE, CtRole.TYPE_PARAMETER))
			//and source fragment BODY
			.fragment(FragmentType.BODY,
					//contains source code of elements on role TYPE_MEMBER
					//and starts when TokenWriter is going to print separator '{'
					//and ends when TokenWriter printed separator '}'
					i -> i.list(CtRole.TYPE_MEMBER).startWhenSeparator("{").endWhenSeparator("}")),
			type(CtExecutable.class)
			.fragment(FragmentType.MODIFIERS,
					i -> i.role(CtRole.ANNOTATION, CtRole.MODIFIER))
			.fragment(FragmentType.BEFORE_NAME,
					i -> i.role(CtRole.TYPE).startWhenScan(CtRole.TYPE_PARAMETER, CtRole.TYPE))
			.fragment(FragmentType.NAME,
					i -> i.role(CtRole.NAME).startWhenIdentifier().endWhenIdentifier())
			.fragment(FragmentType.AFTER_NAME,
					i -> i.role(CtRole.PARAMETER, CtRole.THROWN).startWhenSeparator("("))
			.fragment(FragmentType.BODY,
					i -> i.role(CtRole.BODY).startWhenSeparator("{").endWhenSeparator("}")),
//			type(CtBlock.class)
//			.fragment(FragmentType.MAIN_FRAGMENT,
//					i -> i.list(CtRole.STATEMENT)),
			type(CtVariable.class)
			.fragment(FragmentType.MODIFIERS,
					i -> i.role(CtRole.ANNOTATION, CtRole.MODIFIER))
			.fragment(FragmentType.BEFORE_NAME,
					i -> i.role(CtRole.TYPE).startWhenScan(CtRole.TYPE))
			.fragment(FragmentType.NAME,
					i -> i.role(CtRole.NAME).startWhenIdentifier().endWhenIdentifier())
			.fragment(FragmentType.AFTER_NAME,
					i -> i.role(CtRole.DEFAULT_EXPRESSION))
	);

	/**
	 * Marks the {@link SourceFragment}s, which contains source code of `changedRoles` of `element`
	 * @param element the element
	 * @param fragments
	 * @param changedRoles
	 * @return true if {@link SourceFragment}s matches all changed roles, so we can use them
	 * false if current  `descriptors` is insufficient and we cannot use origin source code of any fragment
	 */
	private static boolean markChangedFragments(CtElement element, SourceFragment fragment, Set<CtRole> changedRoles) {
		for (TypeToFragmentDescriptor descriptor : descriptors) {
			if (descriptor.matchesElement(element)) {
				Set<CtRole> toBeAssignedRoles = new HashSet<>(changedRoles);
				while (fragment != null) {
					//check if this fragment is modified
					FragmentDescriptor fd = descriptor.fragmentToRoles.get(fragment.getFragmentType());
					if (fd != null) {
						//detect if `fragment` is modified and setup fragment start/end detectors
						fd.applyTo(fragment, toBeAssignedRoles);
					}
					fragment = fragment.getNextFragmentOfSameElement();
				}
				//we can use it if all changed roles are matching to some fragment
				if (toBeAssignedRoles.isEmpty()) {
					return true;
				}
				element.getFactory().getEnvironment().debugMessage("The element of type " + element.getClass().getName() + " is not mapping these roles to SourceFragments: " + toBeAssignedRoles);
				return false;
			}
		}
		return false;
	}

	/**
	 * @param element {@link CtElement} whose source position is needed
	 * @return first defined source position of element or null if there is no source position
	 */
	public static SourcePositionImpl getMyOrParentsSourcePosition(CtElement element) {
		while (true) {
			if (element == null) {
				return null;
			}
			SourcePosition sp = element.getPosition();
			if (sp instanceof SourcePositionImpl) {
				return (SourcePositionImpl) sp;
			}
			if (element.isParentInitialized() == false) {
				return null;
			}
			element = element.getParent();
		}
	}
}
