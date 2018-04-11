Structure:
-----------------
* Element has one Position
* Position has one or more Fragments (for example fragments of CtClass are: modifiers, name, extends, implements, body)
* Fragment has startOffset, endOffset and one or more CtRoles (for exmaple CtRoles of CtClass.modifiers are annotation, modifier). CtRole comment is everywhere. 

We know which element is actually printed. DJPP#enter/exit can be used to detect that.

We need to know which Fragment of element is actually printed. E.g. by listenning on TokenWriter#writerKeyword

If no attribute of fragment of printed element is modified, then fragment can be copied from origin source code
If any attribute of fragment of printed element is modified, then fragment must be printed by DJPP

How to detect that DJPP finished with one SourceFragment and is going to start with next SourceFragment
* the defined TokenWriter method with defined value is called. For example:
  * keyword 'class' ends the modifiers section 
  * separator '{' starts TypeMembers of CtType
* the DJPP#scan with an element whose CtElement#getRoleInParent returns expected CtRole. For example:
  * element with parent CtRole#TYPE is scanned from CtExecutable, when modifiers of method are finished
* the DJPP or ElementPrinterHelper will call a new listener to notify that fragment/role is finished
* the Spoon model will fire an event when getter of a property is called.

The current SourceFragment can listen on events to detect whether fragment ended
The next SourceFragment can listen on events to detect whether fragment started

Handling of comments:
-----------------
Comments can be everywhere, they are like tokens in the token stream.
So the List of SourceFragments, which describe parts of origin source of current element should contain a SourceFragment for each comment too
Note: we should care only about comments dirrectly belonging to current element. We have to ignore the comments which belongs to child elements

Then we can detect which CtComment belongs to which SourceFragment and add/remove/modify it if needed.

Handling of lists
-----------------
If the list itself is not modified (add/removed/moved item), just some existing item is modified,
then we should keep source code of list start/end and all item separators

If the list itself is not modified (add/removed/moved item), just some existing item is modified,

All the SourcePosition elements might be connected into a tree using
SourcePosition.nextSibling
SourcePosition.firstChild
