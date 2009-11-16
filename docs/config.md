---
layout: default
title: Configuration
---

Configuration
=============

Bindgen has several configurable parameters that are documented here.

bindgen.properties
------------------

Bindgen's configuration is stored in a `bindgen.properties` file at the root of your project.

Technically, the APT API provides a way to pass key/value pairs to processors via compiler arguments (e.g. `-Akey=value`), but this proves less than ideal when you have more than a few properties and also multiple tools compiling your code (e.g. having to configure the `build.xml`, Eclipse `.settings`, etc.)

So you can use `-Akey=value` arguments, but a separate `bindgen.properties` file is recommended.

Block Types to Attempt
----------------------

Besides fields/getters/setters, Bindgen can generate callable bindings.

Out of the box, Bindgen will wrap `void`, no-argument methods like `void foo()` into a `Runnable foo()` that invokes the original `foo` when the Runnable `run()` is called.

To customize the pseudo-closure like types Bindgen inspects your methods for, set `blockTypes`, e.g.:

<pre name="code">
    blockTypes=com.myapp.Block
</pre>

The `com.myapp.Block` type should have on method. Note that it is allowed to have parameters.

Skip Attribute
--------------

Sometimes you just don't want a property to have a binding generated for it. In this case, set:

    skipAttribute.com.myapp.FooClass.barProperty=true

Fix Raw Types
-------------

Occasionally an old API will use generic classes but without the generics. This can cause a headache to bind against, so you can override the missing generic...

