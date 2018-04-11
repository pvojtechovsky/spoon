package spoon.test.prettyprinter;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import spoon.Launcher;
import spoon.experimental.modelobs.SourceFragmentsTreeCreatingChangeCollector;
import spoon.reflect.cu.CompilationUnit;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.printer.change.ChangesAwareDefaultJavaPrettyPrinter;
import spoon.test.prettyprinter.testclasses.ToBeChanged;
import spoon.testing.utils.ModelUtils;

public class PrintChangesTest {

	@Test
	public void testPrintUnchaged() throws Exception {
		CtType<?> ctClass = ModelUtils.buildClass(ToBeChanged.class);
		
		Factory f = ctClass.getFactory();
		
		new SourceFragmentsTreeCreatingChangeCollector().attachTo(f.getEnvironment());
		
		ChangesAwareDefaultJavaPrettyPrinter printer = new ChangesAwareDefaultJavaPrettyPrinter(f.getEnvironment());
		CompilationUnit cu = f.CompilationUnit().getOrCreate(ctClass);
		List<CtType<?>> toBePrinted = new ArrayList<>();
		toBePrinted.add(ctClass);

		printer.calculate(cu, toBePrinted);

		assertEquals(ctClass.getPosition().getCompilationUnit().getOriginalSourceCode(), printer.getResult());
	}

	@Test
	public void testPrintChanged() throws Exception {
		Launcher launcher = new Launcher();
		launcher.addInputResource("./src/test/java/spoon/test/prettyprinter/testclasses/ToBeChanged.java");
		launcher.getEnvironment().setCommentEnabled(true);
		launcher.getEnvironment().setAutoImports(true);
		launcher.buildModel();
		Factory f = launcher.getFactory();

		final CtClass<?> ctClass = launcher.getFactory().Class().get(ToBeChanged.class);
		
		new SourceFragmentsTreeCreatingChangeCollector().attachTo(f.getEnvironment());
		//change the model
		ctClass.getField("string").setSimpleName("modified");
		
		ChangesAwareDefaultJavaPrettyPrinter printer = new ChangesAwareDefaultJavaPrettyPrinter(f.getEnvironment());
		CompilationUnit cu = f.CompilationUnit().getOrCreate(ctClass);
		List<CtType<?>> toBePrinted = new ArrayList<>();
		toBePrinted.add(ctClass);

		printer.calculate(cu, toBePrinted);

		assertEquals(ctClass.getPosition().getCompilationUnit().getOriginalSourceCode(), printer.getResult());
	}
}
