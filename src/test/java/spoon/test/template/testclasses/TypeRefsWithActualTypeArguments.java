package spoon.test.template.testclasses;

public class TypeRefsWithActualTypeArguments extends SomeType<Double>{
	SomeType<Long> withLong;
	SomeType<? extends Integer> withExtendsInt;
	SomeType<? super String> withSuperString;
	SomeType<?> withWild;
	SomeType withoutActualTypeArgs;
}

class SomeType<T> {}
