package spoon.reflect.visitor.printer.change;

import spoon.reflect.declaration.CtElement;
import spoon.reflect.path.CtRole;
import spoon.reflect.visitor.DefaultJavaPrettyPrinter;

/**
 * Knows how to handle actually printed {@link CtElement} or it's part
 */
abstract class SourceFragmentContext {
	/**
	 * Called when TokenWriter token is sent by {@link DefaultJavaPrettyPrinter}
	 * @param tokenWriterMethodName the name of token method
	 * @param token the value of token
	 * @param printAction the {@link Runnable}, which will send the token to the output
	 */
	abstract void onTokenWriterToken(String tokenWriterMethodName, String token, Runnable printAction);
	/**
	 * Called when {@link DefaultJavaPrettyPrinter} starts scanning of `element` on the parent`s role `role`
	 * @param element to be scanned element
	 * @param role the attribute where the element is in parent
	 * @param printAction the {@link Runnable}, which will scan that element in {@link DefaultJavaPrettyPrinter}
	 */
	abstract void onScanElementOnRole(CtElement element, CtRole role, Runnable printAction);
	/**
	 * Called when this is child context and parent context is just going to finish it's printing
	 */
	abstract void onParentFinished();
}
