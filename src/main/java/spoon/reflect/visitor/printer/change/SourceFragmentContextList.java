package spoon.reflect.visitor.printer.change;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import spoon.reflect.declaration.CtElement;
import spoon.reflect.meta.impl.RoleHandlerHelper;
import spoon.reflect.path.CtRole;

/**
 * Handles printing of changes of the list of elements.
 * E.g. list of type members of type
 */
class SourceFragmentContextList extends SourceFragmentContext {
	private final MutableTokenWriter mutableTokenWriter;
	private final SourceFragment rootFragment;
	private final Map<CtElement, String> listElementToPrefixSpace = new IdentityHashMap<>();
	private final List<Runnable> separatorActions = new ArrayList<>();
	//-1 means print start list separator
	//0..n print elements of list
	private int elementIndex = -1;

	/**
	 * @param mutableTokenWriter {@link MutableTokenWriter}, which is used for printing
	 * @param element the {@link CtElement} whose list attribute is handled
	 * @param rootFragment the {@link SourceFragment}, which represents whole list of elements. E.g. body of method or all type members of type
	 */
	SourceFragmentContextList(MutableTokenWriter mutableTokenWriter, CtElement element, SourceFragment rootFragment) {
		super();
		this.mutableTokenWriter = mutableTokenWriter;
		this.rootFragment = rootFragment;
		CtRole listRole = rootFragment.fragmentDescriptor.getListRole();
		List<CtElement> listElements = RoleHandlerHelper.getRoleHandler(element.getClass(), listRole).asList(element);
		for (CtElement ctElement : listElements) {
			SourceFragment elementFragment = SourcePositionUtils.getSourceFragmentOfElement(ctElement);
			if (elementFragment != null) {
				String prefixSpace = getPrefixSpace(rootFragment, elementFragment);
				if (prefixSpace != null) {
					listElementToPrefixSpace.put(ctElement, prefixSpace);
				}
			}
		}
	}

	/**
	 * @param rootFragment
	 * @param elementFragment
	 * @return the spaces, new lines and separators located in the list of elements before the `elementFragment`
	 */
	private String getPrefixSpace(SourceFragment rootFragment, SourceFragment elementFragment) {
		SourceFragment child = rootFragment.getFirstChild();
		SourceFragment lastChild = null;
		while (child != null) {
			if (child == elementFragment) {
				return lastChild == null ? null : rootFragment.getSourceCode(lastChild.getEnd(), elementFragment.getStart());
			}
			lastChild = child;
			child = child.getNextSibling();
		}
		return null;
	}

	@Override
	void onTokenWriterToken(String tokenWriterMethodName, String token, Runnable printAction) {
		if (elementIndex == -1) {
			if (mutableTokenWriter.isMuted() == false) {
				//print list prefix
				String prefix = rootFragment.getTextBeforeFirstChild();
				if (prefix != null) {
					//we have origin source code for that
					mutableTokenWriter.getPrinterHelper().directPrint(prefix);
					//ignore all list prefix tokens
					mutableTokenWriter.setMuted(true);
				}
			}
			//print the list prefix actions. It can be more token writer events ...
			//so enter it several times, by several calls of onTokenWriterToken by caller. Note: it may be muted, then these tokens are ignored
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
		mutableTokenWriter.setMuted(false);
		elementIndex++;
		if (elementIndex > 0) {
			// print spaces between elements
			String prefixSpaces = listElementToPrefixSpace.get(element);
			if (prefixSpaces != null) {
				//there is origin fragment for `element`
				if (prefixSpaces != null) {
					//there are origin spaces before this `element`
					//use them
					mutableTokenWriter.getPrinterHelper().directPrint(prefixSpaces);
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
		mutableTokenWriter.setMuted(true);
	}

	@Override
	void onParentFinished() {
		//we are at the end of the list of elements. Printer just tries to print list suffix and parent fragment detected that
		mutableTokenWriter.setMuted(false);
		//print list suffix
		String suffix = rootFragment.getTextAfterLastChild();
		if (suffix != null) {
			//we have origin source code for that list suffix
			mutableTokenWriter.getPrinterHelper().directPrint(suffix);
			separatorActions.clear();
		} else {
			//printer must print the spaces and suffix. Send collected events
			printStandardSpaces();
		}
	}

	/**
	 * print all tokens, which represents separator of items or suffix of last item
	 * and then forget them, so we can collect next tokens.
	 */
	private void printStandardSpaces() {
		for (Runnable runnable : separatorActions) {
			runnable.run();
		}
		separatorActions.clear();
	}
}
