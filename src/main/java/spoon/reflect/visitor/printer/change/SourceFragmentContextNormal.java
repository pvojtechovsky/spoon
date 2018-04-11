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
	private final MutableTokenWriter mutableTokenWriter;
	private SourceFragment currentFragment;
	private CtElement element;
	/**
	 * handles printing of lists of elements. E.g. type members of type.
	 */
	private SourceFragmentContext childContext;

	SourceFragmentContextNormal(MutableTokenWriter mutableTokenWriter, CtElement element, SourceFragment rootFragment) {
		super();
		this.mutableTokenWriter = mutableTokenWriter;
		this.element = element;
		this.currentFragment = rootFragment;
		handlePrinting();
	}

	SourceFragmentContextNormal() {
		mutableTokenWriter = null;
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
				mutableTokenWriter.getPrinterHelper().directPrint(currentFragment.getSourceCode());
				mutableTokenWriter.setMuted(true);
			} else {
				//we are printing modified fragment.
				mutableTokenWriter.setMuted(false);
				switch (currentFragment.fragmentDescriptor.kind) {
				case NORMAL:
					//Let it print normally
					break;
				case LIST:
					//we are printing list, create a child context for the list
					childContext = new SourceFragmentContextList(mutableTokenWriter, element, currentFragment);
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
		if (childContext != null) {
			childContext.onTokenWriterToken(tokenWriterMethodName, token, printAction);
		} else {
			//run the print action, which we are listening for
			printAction.run();
		}
		if (testFagmentDescriptor(currentFragment, fd -> fd.isTriggeredByToken(false, tokenWriterMethodName, token))) {
			//yes, the next fragment should be activated before printAction
			if (childContext != null) {
				//notify childContext that it finished
				childContext.onParentFinished();
				childContext = null;
			}
			nextFragment();
		}
	}

	@Override
	void onScanElementOnRole(CtElement element, CtRole role, Runnable printAction) {
		if (testFagmentDescriptor(getNextFragment(), fd -> fd.isStartedByScanRole(role))) {
			//yes, the next fragment should be activated before printAction
			nextFragment();
		}
		//run the print action, which we are listening for
		if (childContext != null) {
			childContext.onScanElementOnRole(element, role, printAction);
		} else {
			printAction.run();
		}
//			{
//				//check if current fragment has to be finished after this action
//				if (currentFragment.fragmentDescriptor.isFinishedByScanRole(role)) {
//					nextFragment();
//				}
//			}
	}

	@Override
	void onParentFinished() {
		//we will see if it is true... I (Pavel) am not sure yet
		throw new SpoonException("SourceFragmentContextNormal shouldn't be used as child context");
	}
}
