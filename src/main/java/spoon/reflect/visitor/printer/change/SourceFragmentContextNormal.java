package spoon.reflect.visitor.printer.change;

import java.util.function.Predicate;

import spoon.SpoonException;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.path.CtRole;
import spoon.reflect.visitor.printer.change.SourcePositionUtils.FragmentDescriptor;

class SourceFragmentContextNormal extends SourceFragmentContext {
	/**
	 *
	 */
	private final ChangesAwareDefaultJavaPrettyPrinter printer;
	private SourceFragment currentFragment;
	private CtElement element;

	SourceFragmentContextNormal(ChangesAwareDefaultJavaPrettyPrinter changesAwareDefaultJavaPrettyPrinter, CtElement element, SourceFragment rootFragment) {
		super();
		printer = changesAwareDefaultJavaPrettyPrinter;
		this.element = element;
		this.currentFragment = rootFragment;
		handlePrinting();
	}

	SourceFragmentContextNormal(ChangesAwareDefaultJavaPrettyPrinter changesAwareDefaultJavaPrettyPrinter) {
		printer = changesAwareDefaultJavaPrettyPrinter;
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
				printer.mutableTokenWriter.getPrinterHelper().directPrint(currentFragment.toString());
				printer.mutableTokenWriter.setMuted(true);
			} else {
				//we are printing modified fragment.
				switch (currentFragment.fragmentDescriptor.kind) {
				case NORMAL:
					//Let it print normally
					printer.mutableTokenWriter.setMuted(false);
					break;
				case LIST:
					//we are printing list
					//TODO
					printer.mutableTokenWriter.setMuted(false);
					break;
				default:
					throw new SpoonException("Unexpected fragment kind " + currentFragment.fragmentDescriptor.kind);
				}
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

	@Override
	void onTokenWriterToken(String tokenWriterMethodName, String token, Runnable printAction) {
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

	@Override
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
