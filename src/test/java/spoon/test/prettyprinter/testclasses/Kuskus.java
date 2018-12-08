package spoon.test.prettyprinter.testclasses;

import spoon.reflect.annotations.PropertySetter;
import spoon.reflect.path.CtRole;

public interface Kuskus {
	@PropertySetter(role = CtRole.TYPE_ARGUMENT)
	<T extends Kuskus> T setActualTypeArguments();
}
