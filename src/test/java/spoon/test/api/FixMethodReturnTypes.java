package spoon.test.api;


import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import spoon.Launcher;
import spoon.metamodel.Metamodel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtReturn;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtTypeParameter;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeParameterReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.compiler.FileSystemFolder;
import spoon.support.modelobs.ChangeCollector;
import spoon.support.sniper.SniperJavaPrettyPrinter;
import spoon.support.util.internal.MapUtils;

public class FixMethodReturnTypes {

	public static void main(String[] args) {
		final Launcher launcher = new Launcher();
		launcher.getEnvironment().setPrettyPrinterCreator(() -> {
			return new SniperJavaPrettyPrinter(launcher.getEnvironment()); }
		);
		launcher.getEnvironment().setNoClasspath(false);
		launcher.getEnvironment().setCommentEnabled(true);
		launcher.getEnvironment().setAutoImports(true);
//		// Spoon model interfaces
		Arrays.asList("spoon/reflect/code",
				"spoon/reflect/declaration",
				"spoon/reflect/reference",
				"spoon/support/reflect/declaration",
				"spoon/support/reflect/code",
				"spoon/support/reflect/reference").forEach(path ->
			launcher.addInputResource(new FileSystemFolder(new File("src/main/java", path))));
		launcher.buildModel();
		Factory factory = launcher.getFactory();
		
		Map<String, Set<CtMethod<?>>> tobeChangedMethods = new HashMap<>();

		long startTime = System.currentTimeMillis();
		
		ChangeCollector.runWithoutChangeListener(launcher.getEnvironment(), () -> {
//			Metamodel mm = new MyMetamodel(factory);
			MetamodelTest.forEachMethodWithInvalidReturnType(factory, mmMethod -> {
				MapUtils.getOrCreate(tobeChangedMethods, mmMethod.getDeclaringType().getQualifiedName(), () -> Collections.newSetFromMap(new IdentityHashMap<>())).add(mmMethod);
			});
		});
		
		
		System.out.println("Collecting of methods done after " + (System.currentTimeMillis() - startTime) + " ms at " + new Date());
		
		for (Map.Entry<String, Set<CtMethod<?>>> e : tobeChangedMethods.entrySet()) {
			System.out.println(e.getKey() + ":" + e.getValue().stream().map(CtMethod::getSignature).sorted().collect(Collectors.joining(", ")));
			for (CtMethod<?> method : e.getValue()) {
				fixMethod(method);
			}
		}
		
		File outputDir = new File(args[0]);
		System.out.println("Printing output to " + outputDir.getAbsolutePath());		
		assertTrue(outputDir.getAbsolutePath(), outputDir.exists());
		launcher.setSourceOutputDirectory(outputDir);
		//print the changed model
		launcher.prettyprint();
	}
	
	static class MyMetamodel extends Metamodel {

		protected MyMetamodel(Factory factory) {
			super(factory);
		}
	}

	private static void fixMethod(CtMethod method) {
		if (method.getSimpleName().equals("setTarget")) {
			if (method.getDeclaringType().getSimpleName().equals("CtFieldAccessImpl")) {
				FixMethodReturnTypes.class.getClass();
			}
		}
		CtTypeReference<?> oldTypeRef = method.getType();
		CtTypeParameter typeParam = null;
		if (oldTypeRef instanceof CtTypeParameterReference) {
			CtTypeParameterReference oldTypeParamRef = (CtTypeParameterReference) oldTypeRef;
			typeParam = oldTypeParamRef.getTypeParameterDeclaration();
		}
		CtType<?> declaringType = method.getDeclaringType();
		CtTypeReference<?> typeRef = declaringType.getReference();
		for (CtTypeParameter ctTypeParam : declaringType.getFormalCtTypeParameters()) {
			typeRef.addActualTypeArgument(ctTypeParam.getReference());
		}
		method.setType(typeRef);
		if (typeParam != null && typeParam.getParent() == method) {
			method.removeFormalCtTypeParameter(typeParam);
		}
		//remove type cast to e.g. `C` in `return (C) this;`
		if (method.getBody()!=null) {
			method.getBody().filterChildren(new TypeFilter<>(CtReturn.class)).forEach((CtReturn r) -> {
				List<CtTypeReference> typeCasts = new ArrayList<>();
				if (r.getReturnedExpression() instanceof CtInvocation) {
					typeCasts.add(typeRef.clone());
				}
				r.getReturnedExpression().setTypeCasts(typeCasts);
			});
		}
	}
}
