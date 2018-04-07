package spoon.reflect.visitor.printer.change;

import spoon.reflect.path.CtRole;

abstract class SourceFragmentContext {
	abstract void onTokenWriterToken(String tokenWriterMethodName, String token, Runnable printAction);
	abstract void onScanRole(CtRole role, PrintAction printAction);
}
