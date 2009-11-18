package org.bindgen.processor.generators;

import static org.bindgen.processor.CurrentEnv.getFiler;
import static org.bindgen.processor.CurrentEnv.getMessager;
import static org.bindgen.processor.CurrentEnv.getTypeUtils;

import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.annotation.Generated;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.JavaFileObject;
import javax.tools.Diagnostic.Kind;

import joist.sourcegen.GClass;
import joist.sourcegen.GMethod;
import joist.util.Copy;

import org.bindgen.Bindable;
import org.bindgen.Binding;
import org.bindgen.processor.CurrentEnv;
import org.bindgen.processor.GenerationQueue;
import org.bindgen.processor.Processor;
import org.bindgen.processor.util.BoundClass;
import org.bindgen.processor.util.Util;

/** Generates a <code>XxxBinding</code> class for a given {@link TypeElement}.
 *
 * Two classes are generated: one class is an abstract <code>XxxBindingPath</code>
 * which has a generic parameter <code>R</code> to present one part in
 * a binding evaluation path rooted at type a type <code>R</code>.
 *
 * The second class is the <ocde>XxxBinding</code> which extends its
 * <code>XxxBindingPath</code> but provides the type parameter <code>R</code>
 * as <code>Xxx</code>, meaning that <code>XxxBinding</code> can be
 * used as the starting point for binding paths rooted at a <code>Xxx</code>.
 */
public class BindingClassGenerator {

	private final GenerationQueue queue;
	private final TypeElement element;
	private final TypeMirror baseElement;
	private final BoundClass name;
	private final List<String> foundSubBindings = new ArrayList<String>();
	private final List<String> done = new ArrayList<String>();
	private GClass pathBindingClass;
	private GClass rootBindingClass;

	public BindingClassGenerator(GenerationQueue queue, TypeElement element) {
		this.queue = queue;
		this.element = element;
		this.name = new BoundClass(element.asType());
		this.baseElement = Util.isOfTypeObjectOrNone(this.element.getSuperclass()) ? null : this.element.getSuperclass();
	}

	public void generate() {
		this.initializePathBindingClass();
		this.addGetName();
		this.addGetType();
		this.generateProperties();
		this.addGetChildBindings();

		this.initializeRootBindingClass();
		this.addConstructors();
		this.addGetWithRoot();

		this.addGeneratedTimestamp();
		this.saveCode(this.pathBindingClass);
		this.saveCode(this.rootBindingClass);
	}

	private void initializePathBindingClass() {
		this.pathBindingClass = new GClass(this.name.getBindingPathClassDeclaration());
		this.pathBindingClass.baseClassName(this.name.getBindingPathClassSuperClass());
		this.pathBindingClass.setAbstract();
	}

	private void initializeRootBindingClass() {
		this.rootBindingClass = new GClass(this.name.getBindingRootClassDeclaration());
		this.rootBindingClass.baseClassName(this.name.getBindingRootClassSuperClass());
	}

	private void addGetWithRoot() {
		GMethod getWithRoot = this.rootBindingClass.getMethod("getWithRoot").argument(this.name.get(), "root").returnType(this.name.get());
		getWithRoot.body.line("return root;");
	}

	private void addGeneratedTimestamp() {
		String value = Processor.class.getName();
		String date = new SimpleDateFormat("dd MMM yyyy hh:mm").format(new Date());
		this.pathBindingClass.addImports(Generated.class);
		this.pathBindingClass.addAnnotation("@Generated(value = \"" + value + "\", date = \"" + date + "\")");
		this.rootBindingClass.addImports(Generated.class);
		this.rootBindingClass.addAnnotation("@Generated(value = \"" + value + "\", date = \"" + date + "\")");
	}

	private void addConstructors() {
		this.rootBindingClass.getConstructor();
		this.rootBindingClass.getConstructor(this.name.get() + " value").body.line("this.set(value);");
	}

	private void addGetName() {
		GMethod getName = this.pathBindingClass.getMethod("getName").returnType(String.class).addAnnotation("@Override");
		getName.body.line("return \"\";");
	}

	private void addGetType() {
		GMethod getType = this.pathBindingClass.getMethod("getType").returnType("Class<?>").addAnnotation("@Override");
		getType.body.line("return {}.class;", this.element.getSimpleName());
	}

	private void generateProperties() {
		for (TypeElement e : Copy.list(this.element).with(this.getSuperElements())) {
			this.generatePropertiesForType(e);
		}
	}

	private void generatePropertiesForType(TypeElement element) {
		for (PropertyGenerator pg : this.getPropertyGenerators(element)) {
			if (this.doneAlreadyContainsPropertyFromSubClass(pg)) {
				continue;
			}
			pg.generate();
			this.markDone(pg);
			this.enqueuePropertyTypeIfNeeded(pg);
			this.addToSubBindingsIfNeeded(pg);
		}
	}

	// in case a parent class has the same field/method name as a child class
	private boolean doneAlreadyContainsPropertyFromSubClass(PropertyGenerator pg) {
		return this.done.contains(pg.getPropertyName());
	}

	private void markDone(PropertyGenerator pg) {
		this.done.add(pg.getPropertyName());
	}

	private void enqueuePropertyTypeIfNeeded(PropertyGenerator pg) {
		if (pg.getPropertyTypeElement() != null) {
			if (CurrentEnv.getConfig().shouldGenerateBindingFor(pg.getPropertyTypeElement())) {
				this.queue.enqueueIfNew(pg.getPropertyTypeElement());
			}
		}
	}

	private void addToSubBindingsIfNeeded(PropertyGenerator pg) {
		if (!pg.isCallable()) {
			this.foundSubBindings.add(pg.getPropertyName());
		}
	}

	private void addGetChildBindings() {
		this.pathBindingClass.addImports(Binding.class, List.class);
		GMethod children = this.pathBindingClass.getMethod("getChildBindings").returnType("List<Binding<?>>").addAnnotation("@Override");
		children.body.line("List<Binding<?>> bindings = new java.util.ArrayList<Binding<?>>();");
		for (String foundSubBinding : this.foundSubBindings) {
			children.body.line("bindings.add(this.{}());", foundSubBinding);
		}
		children.body.line("return bindings;");
	}

	private List<TypeElement> getSuperElements() {
		List<TypeElement> elements = new ArrayList<TypeElement>();
		TypeMirror current = this.baseElement;
		while (current != null && !Util.isOfTypeObjectOrNone(current)) {
			TypeElement currentElement = (TypeElement) getTypeUtils().asElement(current);
			if (currentElement != null) { // javac started returning null, not sure why as Eclipse had not done that
				elements.add(currentElement);
				current = currentElement.getSuperclass();
			} else {
				current = null;
			}
		}
		return elements;
	}

	private void saveCode(GClass gc) {
		try {
			JavaFileObject jfo = getFiler().createSourceFile(gc.getFullClassNameWithoutGeneric(), this.getSourceElements());
			Writer w = jfo.openWriter();
			w.write(gc.toCode());
			w.close();
			this.queue.log("Saved " + gc.getFullClassNameWithoutGeneric());
		} catch (IOException io) {
			getMessager().printMessage(Kind.ERROR, io.getMessage());
		}
	}

	private Element[] getSourceElements() {
		int i = 0;
		Element[] sourceElements = new Element[this.getSuperElements().size() + 1];
		sourceElements[i++] = this.element;
		for (TypeElement superElement : this.getSuperElements()) {
			sourceElements[i++] = superElement;
		}
		return sourceElements;
	}

	private List<PropertyGenerator> getPropertyGenerators(TypeElement type) {
		List<PropertyGenerator> generators = new ArrayList<PropertyGenerator>();
		for (Element enclosed : type.getEnclosedElements()) {

			boolean generate = true;

			if (enclosed.getModifiers().contains(Modifier.STATIC)) {
				generate = false;
			} else if (enclosed.getModifiers().contains(Modifier.PRIVATE)) {
				generate = false;
			} else if (!enclosed.getModifiers().contains(Modifier.PUBLIC)) {
				// protected or package-private, only process if they have their own @bindable
				if (enclosed.getAnnotation(Bindable.class) == null) {
					generate = false;
				}
			}

			if (!generate) {
				continue;
			}

			if (enclosed.getKind().isField()) {
				FieldPropertyGenerator fpg = new FieldPropertyGenerator(this.pathBindingClass, enclosed);
				if (fpg.shouldGenerate()) {
					generators.add(fpg);
					continue;
				}
			} else if (enclosed.getKind() == ElementKind.METHOD) {
				MethodPropertyGenerator mpg = new MethodPropertyGenerator(this.pathBindingClass, (ExecutableElement) enclosed);
				if (mpg.shouldGenerate()) {
					generators.add(mpg);
					continue;
				}
				MethodCallableGenerator mcg = new MethodCallableGenerator(this.pathBindingClass, (ExecutableElement) enclosed);
				if (mcg.shouldGenerate()) {
					generators.add(mcg);
					continue;
				}
			}
		}
		return generators;
	}

}