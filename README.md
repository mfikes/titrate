# Titrate

This repository illustrates running tests that span Clojure, regular JVM ClojureScript, and bootstrap ClojureScript.

## Requirements

Java and Node must be set up.

## Using

1. [Download](https://github.com/clojure/clojurescript/releases)  a standalone `cljs.jar` and place it in the `lib` directory.
2. Run any of `script/test-clj`, `script/test-cljs`, or `script/test-bootstrap`.


## Understanding

The `src` tree has a portable codebase that spans Clojure, regular JVM ClojureScript, and bootstrap ClojureScript, including a portable test runner. (The code is an extension of the code in [Portable Macro Musing](http://blog.fikesfarm.com/posts/2015-06-19-portable-macro-musing.html).)

### Clojure

The Clojure test script is the simplest. It sets up the classpath properly and runs the `runner.core` test runner namespace.

### Regular JVM ClojureScript

The ClojureScript test script AOT compiles the production and test code, targeting Node, and runs the result in Node using the test runner as the main entry point.

> Note: Normally ClojureScript tests wouldn't be run this way, and instead would perhaps make use of something like `lein cljsbuild test`, but this simplifies the example code.

One extra twist is that it introduces a custom assert (written in Clojure), illustrating making `(is (char? nil))` look nice in ClojureScript. (This is derived from [Custom Test Asserts in Planck](http://blog.fikesfarm.com/posts/2016-02-25-custom-test-asserts-in-planck.html).)

### Bootstrap ClojureScript

The bootstrap ClojureScript test script AOT compiles only a special namespace designed to bring up a bootstrap runtime environment with `cljs.js`, along with some of the core ClojureScript namespaces, for execution in Node. When in Node,  the special namespace dynamically loads the production and test code (including the same test runner used for the previous two environments) using only bootstrap facilities.

The same custom assert for `(is (char? nil))` is introduced, but it is loaded via the bootstrap infrastructure once in Node.

Additionally, the use of an auxiliary namespace is illustrated which causes Google Closure and other namespaces to be dumped into the `out` directory for use by the bootstrap environment. (In this case the Google Closure `Uri` class is exercised.)



