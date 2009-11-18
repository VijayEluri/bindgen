package org.bindgen.processor;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.annotation.processing.Processor;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import javax.tools.JavaCompiler.CompilationTask;

import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

public class AbstractBindgenTestCase {
	private static final JavaCompiler COMPILER = ToolProvider.getSystemJavaCompiler();

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	protected ClassLoader compile(String... files) throws CompilationErrorException, IOException {
		DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<JavaFileObject>();
		StandardJavaFileManager fileManager = COMPILER.getStandardFileManager(diagnosticCollector, null, null);

		List<File> compilationUnits = new ArrayList<File>(files.length);
		for (String file : files) {
			compilationUnits.add(new File("src/test/template/" + file));
		}

		CompilationTask task = COMPILER.getTask(
			null,
			fileManager,
			diagnosticCollector,
			Arrays.asList("-d", this.tmp.getRoot().getAbsolutePath()),
			null,
			fileManager.getJavaFileObjectsFromFiles(compilationUnits));

		task.setProcessors(Arrays.asList(new Processor[] { new org.bindgen.processor.Processor() }));

		task.call();

		fileManager.close();

		//System.out.println(this.tmp.getRoot().getAbsolutePath());

		for (Diagnostic<? extends JavaFileObject> diag : diagnosticCollector.getDiagnostics()) {
			switch (diag.getKind()) {
				case ERROR:
					throw new CompilationErrorException(diagnosticCollector);
			}
		}

		URLClassLoader loader = new URLClassLoader(new URL[] { this.tmp.getRoot().getAbsoluteFile().toURL() }, this.getClass().getClassLoader());

		return loader;
	}
}