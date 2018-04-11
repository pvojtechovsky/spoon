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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Predicate;

import spoon.SpoonException;
import spoon.compiler.Environment;
import spoon.experimental.modelobs.ChangeCollector;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.path.CtRole;
import spoon.reflect.visitor.DefaultJavaPrettyPrinter;
import spoon.reflect.visitor.TokenWriter;
import spoon.reflect.visitor.printer.change.SourcePositionUtils.FragmentDescriptor;

/**
 * SourcePositionUtils#descriptors
 */
public class ChangesAwareDefaultJavaPrettyPrinter extends DefaultJavaPrettyPrinter {

	private final MutableTokenWriter mutableTokenWriter;
	private final ChangeCollector changeCollector;
	private final Deque<SourceFragmentContext> sourceFragmentContextStack = new ArrayDeque<>();

	public ChangesAwareDefaultJavaPrettyPrinter(Environment env) {
		super(env);
		this.changeCollector = ChangeCollector.getChangeCollector(env);
		if (this.changeCollector == null) {
			throw new SpoonException(ChangeCollector.class.getSimpleName() + " was not attached to the Environment");
		}
		//create a TokenWriter which can be configured to ignore tokens coming from DJPP
		mutableTokenWriter = new MutableTokenWriter(env);
		//wrap that TokenWriter to listen on all incoming events and set wrapped version to DJPP
		setPrinterTokenWriter(createTokenWriterListener(mutableTokenWriter));
	}

	/**
	 * wrap a `tokenWriter` by a proxy which intercepts all {@link TokenWriter} writeXxx(String) calls
	 * and first calls {@link #onTokenWriterWrite(String, String)} and then calls origin `tokenWriter` method
	 * @param tokenWriter to be wrapped {@link TokenWriter}
	 * @return a wrapped {@link TokenWriter}
	 */
	private TokenWriter createTokenWriterListener(TokenWriter tokenWriter) {
		return (TokenWriter) Proxy.newProxyInstance(
				getClass().getClassLoader(),
				new Class[] {TokenWriter.class},
				new InvocationHandler() {
					@Override
					public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
						if (method.getName().startsWith("write")) {
							Class<?>[] paramTypes = method.getParameterTypes();
							if (paramTypes.length == 1 && paramTypes[0] == String.class) {
								onTokenWriterWrite(method.getName(), (String) args[0], () -> {
									method.invoke(tokenWriter, args);
								});
								return proxy;
							}
						}
						Object result = method.invoke(tokenWriter, args);
						if (method.getReturnType() == TokenWriter.class) {
							return proxy;
						}
						return result;
					}
				});
	}

	/**
	 * Is called for each printed token
	 * @param tokenWriterMethodName the name of {@link TokenWriter} method
	 * @param token the actual token value
	 * @param printAction the executor of the action, we are listening for.
	 * @throws Exception
	 */
	protected void onTokenWriterWrite(String tokenWriterMethodName, String token, PrintAction printAction) throws Exception {
		SourceFragmentContext sfc = sourceFragmentContextStack.peek();
		if (sfc != null) {
			sfc.onTokenWriterToken(tokenWriterMethodName, token, printAction);
			return;
		}
		printAction.run();
	}

	private class SourceFragmentContext {
		private SourceFragment currentFragment;
		private CtElement element;

		SourceFragmentContext(CtElement element, SourceFragment rootFragment) {
			super();
			this.element = element;
			this.currentFragment = rootFragment;
			handlePrinting();
		}

		SourceFragmentContext() {
			currentFragment = null;
		}

		SourceFragment getNextFragment() {
			if (currentFragment != null) {
				return currentFragment.getNextFragmentOfSameElement();
			}
			return null;
		}

		/**
		 * Called when next fragment is going to be printed
		 */
		void nextFragment() {
			currentFragment = getNextFragment();
			handlePrinting();
		}

		void handlePrinting() {
			if (currentFragment != null) {
				if (currentFragment.isModified() == false) {
					//we are going to print not modified fragment
					//print origin sources of this fragment directly
					mutableTokenWriter.getPrinterHelper().directPrint(currentFragment.toString());
					mutableTokenWriter.setMuted(true);
				} else {
					//we are printing modified fragment. Let it print normally
					mutableTokenWriter.setMuted(false);
				}
			}
		}

		boolean testFagmentDescriptor(SourceFragment sourceFragment, Predicate<FragmentDescriptor> predicate) {
			if (sourceFragment != null) {
				if (sourceFragment.fragmentDescriptor != null) {
					return predicate.test(sourceFragment.fragmentDescriptor);
				}
			}
			return false;
		}

		void onTokenWriterToken(String tokenWriterMethodName, String token, PrintAction printAction) throws Exception {
			if (testFagmentDescriptor(getNextFragment(), fd -> fd.isTriggeredByToken(true, tokenWriterMethodName, token))) {
				//yes, the next fragment should be activated before printAction
				nextFragment();
			}
			//run the print action, which we are listening for
			printAction.run();
			if (testFagmentDescriptor(currentFragment, fd -> fd.isTriggeredByToken(false, tokenWriterMethodName, token))) {
				//yes, the next fragment should be activated before printAction
				nextFragment();
			}
		}

		void onScanRole(CtRole role, PrintAction printAction) {
			if (testFagmentDescriptor(getNextFragment(), fd -> fd.isStartedByScanRole(role))) {
				//yes, the next fragment should be activated before printAction
				nextFragment();
			}
			//run the print action, which we are listening for
			try {
				printAction.run();
			} catch (SpoonException e) {
				throw (SpoonException) e;
			} catch (Exception e) {
				throw new SpoonException(e);
			}
//			{
//				//check if current fragment has to be finished after this action
//				if (currentFragment.fragmentDescriptor.isFinishedByScanRole(role)) {
//					nextFragment();
//				}
//			}
		}
	}

	private final SourceFragmentContext EMPTY_FRAGMENT_CONTEXT = new SourceFragmentContext();

	@Override
	public ChangesAwareDefaultJavaPrettyPrinter scan(CtElement element) {
		SourceFragmentContext sfc = sourceFragmentContextStack.peek();
		if (sfc != null) {
			CtRole role = element.getRoleInParent();
			if (role != null) {
				sfc.onScanRole(role, () -> scanInternal(element));
				return this;
			}
		}
		scanInternal(element);
		return this;
	}

	private void scanInternal(CtElement element) {
		if (mutableTokenWriter.isMuted()) {
			//it is already muted by an parent. Simply scan and ignore all tokens,
			//because the content is not modified and was already copied from source
			sourceFragmentContextStack.push(EMPTY_FRAGMENT_CONTEXT);
			super.scan(element);
			sourceFragmentContextStack.pop();
			return;
		}
		//it is not muted yet, so some this element or any sibling was modified
		//detect SourceFragments of element and whether they are modified or not
		SourceFragment rootFragmentOfElement = SourcePositionUtils.getSourceFragmentsOfElement(changeCollector, element);
		if (rootFragmentOfElement == null) {
			//we have no origin sources. Use normal printing
			sourceFragmentContextStack.push(EMPTY_FRAGMENT_CONTEXT);
			super.scan(element);
			sourceFragmentContextStack.pop();
			return;
		}
		try {
			SourceFragmentContext sfx = new SourceFragmentContext(element, rootFragmentOfElement);
			sourceFragmentContextStack.push(sfx);
			super.scan(element);
		} finally {
			//at the end we always un-mute the token writer
			mutableTokenWriter.setMuted(false);
			sourceFragmentContextStack.pop();
		}
	}
}
