package spoon.reflect.visitor.printer.change;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import spoon.reflect.declaration.CtElement;
import spoon.reflect.meta.impl.RoleHandlerHelper;
import spoon.reflect.path.CtRole;
import spoon.reflect.visitor.printer.change.SourcePositionUtils.FragmentDescriptor;

/**
 * Handles printing of changes of the list of elements
 */
class SourceFragmentContextList extends SourceFragmentContext {
	private final ChangesAwareDefaultJavaPrettyPrinter printer;
	private final SourceFragment rootFragment;
	private final Map<CtElement, FragmentPair> listElementToFragmentPair = new IdentityHashMap<>();
	private final List<Runnable> separatorActions = new ArrayList<>();
	//-1 means print start list separator
	//0..n print elements of list
	private int elementIndex = -1;

	private static class FragmentPair {
		/**
		 * optional spaces before the element (e.g. spaces before the method or field)
		 */
		String prefixSpaces;
		/**
		 * the fragment of element
		 */
		SourceFragment elementFragment;

		FragmentPair(String prefixSpaces, SourceFragment elementFragment) {
			super();
			this.prefixSpaces = prefixSpaces;
			this.elementFragment = elementFragment;
		}
	}

	SourceFragmentContextList(ChangesAwareDefaultJavaPrettyPrinter changesAwareDefaultJavaPrettyPrinter, CtElement element, SourceFragment rootFragment) {
		super();
		printer = changesAwareDefaultJavaPrettyPrinter;
		this.rootFragment = rootFragment;
		CtRole listRole = rootFragment.fragmentDescriptor.getListRole();
		List<CtElement> listElements = RoleHandlerHelper.getRoleHandler(element.getClass(), listRole).asList(element);
		for (CtElement ctElement : listElements) {
			SourceFragment elementFragment = SourcePositionUtils.getSourceFragmentOfElement(ctElement);
			if (elementFragment != null) {
				FragmentPair fragmentPair = createFragmentPair(rootFragment, elementFragment);
				if (fragmentPair != null) {
					listElementToFragmentPair.put(ctElement, fragmentPair);
				}
			}
		}
	}

	private FragmentPair createFragmentPair(SourceFragment rootFragment, SourceFragment elementFragment) {
		SourceFragment child = rootFragment.getFirstChild();
		SourceFragment lastChild = null;
		while (child != null) {
			if (child == elementFragment) {
				return new FragmentPair(
						lastChild == null ? null : rootFragment.getSourceCode(lastChild.getEnd(), elementFragment.getStart()),
						elementFragment);
			}
			lastChild = child;
			child = child.getNextSibling();
		}
		return null;
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
		if (elementIndex == -1) {
			if (printer.mutableTokenWriter.isMuted() == false) {
				//print list prefix
				String prefix = rootFragment.getTextBeforeFirstChild();
				if (prefix != null) {
					//we have origin source code for that
					printer.mutableTokenWriter.getPrinterHelper().directPrint(prefix);
					//ignore all list prefix tokens
					printer.mutableTokenWriter.setMuted(true);
				}
			}
			//print the list prefix actions. It can be more token writer events ...
			//so enter it several times. Note: it may be muted, then these tokens are ignored
			printAction.run();
		} else {
			//print list separator
			//collect (postpone) printAction of separators and list ending token, because we print them depending on next element / end of list
			separatorActions.add(printAction);
		}
	}

	@Override
	void onScanElementOnRole(CtElement element, CtRole role, Runnable printAction) {
		//the printing of child element must not be muted here
		//it can be muted later internally, if element is not modified
		//but something in list is modified and we do not know what
		printer.mutableTokenWriter.setMuted(false);
		elementIndex++;
		if (elementIndex > 0) {
			// print spaces between elements
			FragmentPair fragmentPair = listElementToFragmentPair.get(element);
			if (fragmentPair != null) {
				//there is origin fragment for `element`
				if (fragmentPair.prefixSpaces != null) {
					//there are origin spaces before this `element`
					//use them
					printer.mutableTokenWriter.getPrinterHelper().directPrint(fragmentPair.prefixSpaces);
					//forget DJPP spaces
					separatorActions.clear();
				}
				/*
				 * else there are no origin spaces we have to let it print normally
				 * - e.g. when new first element is added, then second element has no standard spaces
				 */
			}
		} //else it is the first element of list. Do not print spaces here (we already have spaces after the list prefix)
		printStandardSpaces();
		//run the DJPP scanning action, which we are listening for
		printAction.run();
		//the child element is printed, now it will print separators or list end
		printer.mutableTokenWriter.setMuted(true);
	}

	@Override
	void onParentFinished() {
		//we are the end of the list of elements. Printer just tries to print list suffix and parent fragment detected that
		printer.mutableTokenWriter.setMuted(false);
		//print list suffix
		String suffix = rootFragment.getTextAfterLastChild();
		if (suffix != null) {
			//we have origin source code for that list suffix
			printer.mutableTokenWriter.getPrinterHelper().directPrint(suffix);
			separatorActions.clear();
		} else {
			//printer must print the spaces and suffix. Send collected events
			printStandardSpaces();
		}
	}

	private void printStandardSpaces() {
		for (Runnable runnable : separatorActions) {
			runnable.run();
		}
		separatorActions.clear();
	}
}
