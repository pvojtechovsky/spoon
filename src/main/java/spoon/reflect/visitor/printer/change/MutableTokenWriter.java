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
import spoon.reflect.code.CtComment;
import spoon.reflect.visitor.DefaultTokenWriter;
import spoon.reflect.visitor.TokenWriter;

/**
 * {@link TokenWriter}, which simply delegates
 * all tokens to delegate, until {@link #setMuted(boolean)} is called with true
 * Then all tokens are ignored.
 */
public class MutableTokenWriter implements TokenWriter {
	private final TokenWriter delegate;
	private boolean muted = false;

	public MutableTokenWriter(Environment env) {
		super();
		this.delegate = new DefaultTokenWriter(new DirectPrinterHelper(env));;
	}

	public boolean isMuted() {
		return muted;
	}

	public void setMuted(boolean muted) {
		this.muted = muted;
	}

	public TokenWriter writeSeparator(String token) {
		if (isMuted()) {
			getPrinterHelper().setShouldWriteTabs(false);
			return this;
		}
		delegate.writeSeparator(token);
		return this;
	}
	public TokenWriter writeOperator(String token) {
		if (isMuted()) {
			getPrinterHelper().setShouldWriteTabs(false);
			return this;
		}
		delegate.writeOperator(token);
		return this;
	}
	public TokenWriter writeLiteral(String token) {
		if (isMuted()) {
			getPrinterHelper().setShouldWriteTabs(false);
			return this;
		}
		delegate.writeLiteral(token);
		return this;
	}
	public TokenWriter writeKeyword(String token) {
		if (isMuted()) {
			getPrinterHelper().setShouldWriteTabs(false);
			return this;
		}
		delegate.writeKeyword(token);
		return this;
	}
	public TokenWriter writeIdentifier(String token) {
		if (isMuted()) {
			getPrinterHelper().setShouldWriteTabs(false);
			return this;
		}
		delegate.writeIdentifier(token);
		return this;
	}
	public TokenWriter writeCodeSnippet(String token) {
		if (isMuted()) {
			getPrinterHelper().setShouldWriteTabs(false);
			return this;
		}
		delegate.writeCodeSnippet(token);
		return this;
	}
	public TokenWriter writeComment(CtComment comment) {
		if (isMuted()) {
			getPrinterHelper().setShouldWriteTabs(false);
			return this;
		}
		delegate.writeComment(comment);
		return this;
	}
	public TokenWriter writeln() {
		if (isMuted()) {
			getPrinterHelper().setShouldWriteTabs(true);
			return this;
		}
		delegate.writeln();
		return this;
	}
	public TokenWriter incTab() {
		if (isMuted()) {
			return this;
		}
		delegate.incTab();
		return this;
	}
	public TokenWriter decTab() {
		if (isMuted()) {
			return this;
		}
		delegate.decTab();
		return this;
	}
	public DirectPrinterHelper getPrinterHelper() {
		return (DirectPrinterHelper) delegate.getPrinterHelper();
	}
	public void reset() {
		if (isMuted()) {
			return;
		}
		delegate.reset();
	}
	public TokenWriter writeSpace() {
		if (isMuted()) {
			getPrinterHelper().setShouldWriteTabs(false);
			return this;
		}
		delegate.writeSpace();
		return this;
	}
}
