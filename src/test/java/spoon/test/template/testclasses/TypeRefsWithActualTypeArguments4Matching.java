package spoon.test.template.testclasses;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class TypeRefsWithActualTypeArguments4Matching {
	public void matcher() {
		List<Double> fieldName;
	}
	
	public void example() {
		SomeType<Long> withLong;
		SomeType<? extends Integer> withExtendsInt;
		List<? super String> withSuperString;
		Set<?> withWild;
		Collection withoutActualTypeArgs;
	}
}
