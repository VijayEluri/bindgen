package org.exigencecorp.bindgen.example.inheritance;

import junit.framework.Assert;
import junit.framework.TestCase;

import org.bindgen.Binding;
import org.bindgen.example.inheritance.SubExample;
import org.bindgen.example.inheritance.SubHolder;

import bindgen.org.bindgen.example.inheritance.BaseExampleBinding;
import bindgen.org.bindgen.example.inheritance.SubExampleBinding;
import bindgen.org.bindgen.example.inheritance.SubHolderBinding;

public class SubExampleTest extends TestCase {

	private SubHolder holder = new SubHolder();

	public void testFoo() {
		Assert.assertEquals(null, this.holder.sub);
		SubExample sub = new SubExample();

		SubHolderBinding b = new SubHolderBinding(this.holder);
		Binding<? super SubExample> bind = b.sub();
		bind.set(sub);
		Assert.assertSame(sub, this.holder.sub);

		b.sub().set(sub);
		Assert.assertSame(sub, this.holder.sub);
	}

	public void testSuperAttribute() {
		Assert.assertEquals(null, this.holder.sub);

		SubExample sub = new SubExample();
		sub.description = "existingInSuper";
		SubExampleBinding b = new SubExampleBinding(sub);
		Assert.assertEquals("existingInSuper", b.description().get());

		Assert.assertEquals(3, b.getChildBindings().size()); // name, description, subOnly
	}

	public void testOverriddenCallable() {
		SubExample sub = new SubExample();
		SubExampleBinding b = new SubExampleBinding(sub);
		b.go().run();
		Assert.assertEquals("insub", sub.name);
	}

	public void testOverriddenCallableWithABaseBinding() {
		SubExample sub = new SubExample();
		BaseExampleBinding b = new BaseExampleBinding(sub);
		b.go().run();
		Assert.assertEquals("insub", sub.name);

		Assert.assertTrue(b.get() instanceof SubExample);
		Assert.assertEquals(2, b.getChildBindings().size()); // no subOnly
	}

}