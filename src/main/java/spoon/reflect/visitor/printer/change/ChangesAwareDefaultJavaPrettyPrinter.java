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
import spoon.compiler.Environment;
import spoon.experimental.modelobs.ChangeCollector;
import spoon.reflect.code.CtComment;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.path.CtRole;
import spoon.reflect.visitor.DefaultJavaPrettyPrinter;
import spoon.reflect.visitor.PrettyPrinter;
import spoon.reflect.visitor.PrinterHelper;
import spoon.reflect.visitor.TokenWriter;

/**
 * {@link PrettyPrinter} implementation which copies as much as possible from origin sources
 * and prints only changed elements
 */
public class ChangesAwareDefaultJavaPrettyPrinter extends DefaultJavaPrettyPrinter {

	private final MutableTokenWriter mutableTokenWriter;
	private final ChangeCollector changeCollector;
	private final Deque<SourceFragmentContext> sourceFragmentContextStack = new ArrayDeque<>();

	/**
	 * Creates a new {@link PrettyPrinter} which copies origin sources and prints only changes.
	 */
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
		return new TokenWriterProxy(tokenWriter);
	}

	private class TokenWriterProxy implements TokenWriter {
		private final TokenWriter delegate;

		TokenWriterProxy(TokenWriter delegate) {
			super();
			this.delegate = delegate;
		}

		public TokenWriter writeSeparator(String token) {
			onTokenWriterWrite("writeSeparator", token, null, () -> delegate.writeSeparator(token));
			return this;
		}

		public TokenWriter writeOperator(String token) {
			onTokenWriterWrite("writeOperator", token, null, () -> delegate.writeOperator(token));
			return this;
		}

		public TokenWriter writeLiteral(String token) {
			onTokenWriterWrite("writeLiteral", token, null, () -> delegate.writeLiteral(token));
			return this;
		}

		public TokenWriter writeKeyword(String token) {
			onTokenWriterWrite("writeKeyword", token, null, () -> delegate.writeKeyword(token));
			return this;
		}

		public TokenWriter writeIdentifier(String token) {
			onTokenWriterWrite("writeIdentifier", token, null, () -> delegate.writeIdentifier(token));
			return this;
		}

		public TokenWriter writeCodeSnippet(String token) {
			onTokenWriterWrite("writeCodeSnippet", token, null, () -> delegate.writeCodeSnippet(token));
			return this;
		}

		public TokenWriter writeComment(CtComment comment) {
			onTokenWriterWrite("writeComment", null, comment, () -> delegate.writeComment(comment));
			return this;
		}

		public TokenWriter writeln() {
			onTokenWriterWrite("writeln", "\n", null, () -> delegate.writeln());
			return this;
		}

		public TokenWriter incTab() {
			onTokenWriterWrite("incTab", null, null, () -> delegate.incTab());
			return this;
		}

		public TokenWriter decTab() {
			onTokenWriterWrite("decTab", null, null, () -> delegate.decTab());
			return this;
		}

		public PrinterHelper getPrinterHelper() {
			return delegate.getPrinterHelper();
		}

		public void reset() {
			delegate.reset();
		}

		public TokenWriter writeSpace() {
			onTokenWriterWrite("writeSpace", " ", null, () -> delegate.writeSpace());
			return this;
		}
	}

	/**
	 * Is called for each printed token
	 * @param tokenWriterMethodName the name of {@link TokenWriter} method
	 * @param token the actual token value. It may be null for some `tokenWriterMethodName`
	 * @param comment the comment when `tokenWriterMethodName` == `writeComment`
	 * @param printAction the executor of the action, we are listening for.
	 */
	private void onTokenWriterWrite(String tokenWriterMethodName, String token, CtComment comment, Runnable printAction) {
		SourceFragmentContext sfc = sourceFragmentContextStack.peek();
		if (sfc != null) {
			sfc.onTokenWriterToken(tokenWriterMethodName, token, printAction);
			return;
		}
		printAction.run();
	}

	private final SourceFragmentContextNormal EMPTY_FRAGMENT_CONTEXT = new SourceFragmentContextNormal();

	/**
	 * Called whenever {@link DefaultJavaPrettyPrinter} scans/prints an element
	 */
	@Override
	public ChangesAwareDefaultJavaPrettyPrinter scan(CtElement element) {
		SourceFragmentContext sfc = sourceFragmentContextStack.peek();
		if (sfc != null) {
			CtRole role = element.getRoleInParent();
			if (role != null) {
				//there is an context in the child element, let it handle scanning
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
			//we have no origin sources or this element has one source fragment only and it is modified. Use normal printing
			sourceFragmentContextStack.push(EMPTY_FRAGMENT_CONTEXT);
			super.scan(element);
			sourceFragmentContextStack.pop();
			return;
		}
		//the element is modified and it consists of more source fragments, and some of them are not modified
		//so we can copy the origin sources for them
		SourceFragmentContextNormal sfx = new SourceFragmentContextNormal(mutableTokenWriter, element, rootFragmentOfElement);
		sourceFragmentContextStack.push(sfx);
		super.scan(element);
		sourceFragmentContextStack.pop();
		//at the end we always un-mute the token writer
		mutableTokenWriter.setMuted(false);
	}
}
