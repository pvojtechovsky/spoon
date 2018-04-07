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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.Deque;

import spoon.SpoonException;
import spoon.compiler.Environment;
import spoon.experimental.modelobs.ChangeCollector;
import spoon.reflect.code.CtComment;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.path.CtRole;
import spoon.reflect.visitor.DefaultJavaPrettyPrinter;
import spoon.reflect.visitor.TokenWriter;

/**
 * SourcePositionUtils#descriptors
 */
public class ChangesAwareDefaultJavaPrettyPrinter extends DefaultJavaPrettyPrinter {

	final MutableTokenWriter mutableTokenWriter;
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
						Runnable printAction = () -> {
							try {
								method.invoke(tokenWriter, args);
							} catch (IllegalAccessException | IllegalArgumentException e) {
								throw new SpoonException("Cannot invoke TokenWriter method", e);
							} catch (InvocationTargetException e) {
								if (e.getTargetException() instanceof RuntimeException) {
									throw (RuntimeException) e.getTargetException();
								}
								throw new SpoonException("Invokation target exception TokenWriter method", e);
							}
						};
						if (method.getName().startsWith("write")) {
							Class<?>[] paramTypes = method.getParameterTypes();
							if (paramTypes.length == 1) {
								if (paramTypes[0] == String.class) {
									//writeXxx(String)
									onTokenWriterWrite(method.getName(), (String) args[0], null, printAction);
								} else if (paramTypes[0] == CtComment.class) {
									//writeComment(CtComment)
									onTokenWriterWrite(method.getName(), null, (CtComment) args[0], printAction);
								}
								return proxy;
							} else if (paramTypes.length == 0) {
								//writeSpace() and writeln()
								String token = method.getName().equals("writeSpace") ? " " : "\n";
								onTokenWriterWrite(method.getName(), token, null, printAction);
								return proxy;
							}
						}
						if (method.getName().endsWith("Tab")) {
							//incTab(), decTab()
							onTokenWriterWrite(method.getName(), (String) null, null, printAction);
							return proxy;
						}
						if (method.getName().equals("getPrinterHelper") || method.getName().equals("reset")) {
							try {
								return method.invoke(tokenWriter, args);
							} catch (IllegalAccessException | IllegalArgumentException e) {
								throw new SpoonException("Cannot invoke TokenWriter method", e);
							} catch (InvocationTargetException e) {
								throw e.getTargetException();
							}
						}
						throw new SpoonException("Unexpected method TokenWriter#" + method.getName());
					}
				});
	}

	/**
	 * Is called for each printed token
	 * @param tokenWriterMethodName the name of {@link TokenWriter} method
	 * @param token the actual token value
	 * @param comment TODO
	 * @param printAction the executor of the action, we are listening for.
	 * @throws Exception
	 */
	protected void onTokenWriterWrite(String tokenWriterMethodName, String token, CtComment comment, Runnable printAction) {
		SourceFragmentContext sfc = sourceFragmentContextStack.peek();
		if (sfc != null) {
			sfc.onTokenWriterToken(tokenWriterMethodName, token, printAction);
			return;
		}
		printAction.run();
	}

	private final SourceFragmentContextNormal EMPTY_FRAGMENT_CONTEXT = new SourceFragmentContextNormal(this);

	@Override
	public ChangesAwareDefaultJavaPrettyPrinter scan(CtElement element) {
		SourceFragmentContext sfc = sourceFragmentContextStack.peek();
		if (sfc != null) {
			CtRole role = element.getRoleInParent();
			if (role != null) {
				sfc.onScanElementOnRole(element, role, () -> scanInternal(element));
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
			SourceFragmentContextNormal sfx = new SourceFragmentContextNormal(this, element, rootFragmentOfElement);
			sourceFragmentContextStack.push(sfx);
			super.scan(element);
		} finally {
			//at the end we always un-mute the token writer
			mutableTokenWriter.setMuted(false);
			sourceFragmentContextStack.pop();
		}
	}
}
