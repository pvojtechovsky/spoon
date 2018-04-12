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
import java.util.function.Predicate;

import spoon.SpoonException;
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

/**
 * source code position helper methods
 */
public abstract class SourcePositionUtils  {

	/**
	 * @param element target {@link CtElement}
	 * @return {@link SourceFragment}, which represents origin source code of the `element`
	 */
	static SourceFragment getSourceFragmentOfElement(CtElement element) {
		SourcePosition sp = element.getPosition();
		if (sp.getCompilationUnit() != null) {
			CompilationUnit cu = sp.getCompilationUnit();
			return cu.getSourceFragment(element);
		}
		return null;
	}

	/**
	 * Maps {@link FragmentType} to spoon model concept {@link #type} specific {@link FragmentDescriptor}
	 */
	private static class TypeToFragmentDescriptor {
		Class<? extends CtElement> type;
		Map<FragmentType, FragmentDescriptor> fragmentToRoles = new HashMap<>();
		TypeToFragmentDescriptor(Class<? extends CtElement> type) {
			this.type = type;
		}

		/**
		 * Creates a {@link FragmentDescriptor} of defined {@link FragmentType}
		 * and initializes it using `initializer`
		 * @param ft  {@link FragmentType}
		 * @param initializer a code which defines behavior of that fragment
		 * @return this to support fluent API
		 */
		TypeToFragmentDescriptor fragment(FragmentType ft, Consumer<FragmentDescriptorBuilder> initializer) {
			FragmentDescriptor fd = new FragmentDescriptor();
			initializer.accept(new FragmentDescriptorBuilder(fd));
			fragmentToRoles.put(ft, fd);
			return this;
		}

		/**
		 * @param element target {@link CtElement}
		 * @return true if `element` is handled by this {@link TypeToFragmentDescriptor}
		 */
		boolean matchesElement(CtElement element) {
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
		 * 1) initializes fragmentDescriptors of the {@link SourceFragment}
		 * 2) detects whether `fragment` contains source code of modified element attribute/role
		 *
		 * @param fragment target {@link SourceFragment}
		 * @param changedRoles the modifiable {@link Set} of {@link CtRole}s, whose attributes values are modified in element of the fragment
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
		 * Detects whether {@link TokenWriter} token just printed by {@link DefaultJavaPrettyPrinter}
		 * triggers start/end usage of {@link SourceFragment} linked to this {@link FragmentDescriptor}
		 *
		 * @param isStart if true then it checks start trigger. if false then it checks end trigger
		 * @param tokenWriterMethodName the name of {@link TokenWriter} method whose token is fired
		 * @param token the value of the {@link TokenWriter} token. May be null, depending on the `tokenWriterMethodName`
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
		 * linked to this {@link FragmentDescriptor}
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
		CtRole getListRole() {
			if (kind != FragmentKind.LIST || roles == null || roles.size() != 1) {
				throw new SpoonException("This fragment does not have list role");
			}
			return roles.iterator().next();
		}
	}

	/**
	 * Used to build {@link FragmentDescriptor}s of spoon model concepts in a maintenable and readable way
	 */
	private static class FragmentDescriptorBuilder {
		FragmentDescriptor descriptor;

		FragmentDescriptorBuilder(FragmentDescriptor descriptor) {
			super();
			this.descriptor = descriptor;
		}

		/**
		 * @param roles the one or more roles, whose values are contained in the linked
		 * {@link SourceFragment}. Used to detect whether {@link SourceFragment} is modified or not.
		 */
		FragmentDescriptorBuilder role(CtRole... roles) {
			descriptor.roles = new HashSet(Arrays.asList(roles));
			return this;
		}

		/**
		 * @param role defines {@link FragmentDescriptor} of {@link SourceFragment},
		 * which represents a list of values. E.g. list of type members.
		 */
		FragmentDescriptorBuilder list(CtRole role) {
			if (descriptor.roles != null) {
				throw new SpoonException("Cannot combine #role and #list");
			}
			descriptor.roles = Collections.singleton(role);
			descriptor.kind = FragmentKind.LIST;
			return this;
		}

		/**
		 * @param tokens list of {@link TokenWriter} keywords which triggers start of {@link SourceFragment}
		 * linked by built {@link FragmentDescriptor}
		 */
		FragmentDescriptorBuilder startWhenKeyword(String...tokens) {
			descriptor.startTokenDetector.add(createTokenDetector("writeKeyword", tokens));
			return this;
		}
		/**
		 * @param tokens list of {@link TokenWriter} keywords which triggers end of {@link SourceFragment}
		 * linked by built {@link FragmentDescriptor}
		 */
		FragmentDescriptorBuilder endWhenKeyword(String...tokens) {
			descriptor.endTokenDetector.add(createTokenDetector("writeKeyword", tokens));
			return this;
		}
		/**
		 * @param tokens list of {@link TokenWriter} separators, which triggers start of {@link SourceFragment}
		 * linked by built {@link FragmentDescriptor}
		 */
		FragmentDescriptorBuilder startWhenSeparator(String...tokens) {
			descriptor.startTokenDetector.add(createTokenDetector("writeSeparator", tokens));
			return this;
		}
		/**
		 * @param tokens list of {@link TokenWriter} separators, which triggers end of {@link SourceFragment}
		 * linked by built {@link FragmentDescriptor}
		 */
		FragmentDescriptorBuilder endWhenSeparator(String...tokens) {
			descriptor.endTokenDetector.add(createTokenDetector("writeSeparator", tokens));
			return this;
		}
		/**
		 * Defines that any {@link TokenWriter} identifier triggers start of {@link SourceFragment}
		 * linked by built {@link FragmentDescriptor}
		 */
		FragmentDescriptorBuilder startWhenIdentifier() {
			descriptor.startTokenDetector.add(createTokenDetector("writeIdentifier"));
			return this;
		}
		/**
		 * Defines that any {@link TokenWriter} identifier triggers end of {@link SourceFragment}
		 * linked by built {@link FragmentDescriptor}
		 */
		FragmentDescriptorBuilder endWhenIdentifier() {
			descriptor.endTokenDetector.add(createTokenDetector("writeIdentifier"));
			return this;
		}
		/**
		 * @param roles list of {@link CtRole}s, whose scanning triggers start of {@link SourceFragment}
		 * linked by built {@link FragmentDescriptor}
		 */
		FragmentDescriptorBuilder startWhenScan(CtRole...roles) {
			descriptor.startScanRole.addAll(Arrays.asList(roles));
			return this;
		}

		/**
		 * Creates a {@link Predicate} which detects occurrence of the {@link TokenWriter} token
		 * @param tokenWriterMethodName
		 * @param tokens
		 */
		static BiPredicate<String, String> createTokenDetector(String tokenWriterMethodName, String...tokens) {
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
			return predicate;
		}
	}

	/**
	 * @param type target spoon model concept Class
	 * @return new {@link TypeToFragmentDescriptor} which describes the printing behavior `type`
	 */
	private static TypeToFragmentDescriptor type(Class<? extends CtElement> type) {
		return new TypeToFragmentDescriptor(type);
	}

	/**
	 * Defines the know-how of printing of spoon model concepts
	 */
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
						i -> i.startWhenKeyword("class", "enum", "interface").startWhenSeparator("@"))
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
						//contains source code of elements on role TYPE_MEMBER, which is the list of values
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
//				.fragment(FragmentType.MAIN_FRAGMENT,
//						i -> i.list(CtRole.STATEMENT)),
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
	 * @param element the {@link CtElement} which belongs to the `fragment`
	 * @param fragment the chain of sibling {@link SourceFragment}s, which represent source code fragments of `element`
	 * @param changedRoles the set of roles whose values are actually modified in `element` (so we cannot use origin source code, but have to print them normally)
	 * @return true if {@link SourceFragment}s matches all changed roles, so we can use them
	 * false if current  `descriptors` is insufficient and we cannot use origin source code of any fragment.
	 * It happens because only few spoon model concepts have a {@link FragmentDescriptor}s
	 */
	static boolean markChangedFragments(CtElement element, SourceFragment fragment, Set<CtRole> changedRoles) {
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
				if (toBeAssignedRoles.isEmpty()) {
					//we can use it if all changed roles are matching to some fragment
					return true;
				}
				//check this log if you need to make printing of changes more precise
				element.getFactory().getEnvironment().debugMessage("The element of type " + element.getClass().getName() + " is not mapping these roles to SourceFragments: " + toBeAssignedRoles);
				return false;
			}
		}
		//check this log if you need to make printing of changes more precise
		element.getFactory().getEnvironment().debugMessage("The element of type " + element.getClass().getName() + " has no printing descriptor");
		return false;
	}
}
