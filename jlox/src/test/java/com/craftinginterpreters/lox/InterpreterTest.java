package com.craftinginterpreters.lox;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class InterpreterTest {
  @Test
  void debug() {
    switch(new Scanner("""
stuff();
            """).scanTokens()) {
      case Scanner.LexError lexError -> {
        fail(lexError.getMessage());
      }
      case Scanner.TokenList tokens -> {
        switch(new Parser(tokens).parse()) {
          case ParseError parseError -> {
            fail(parseError.getMessage());
          }
          case Expr expr -> {
            var result = expr.accept(new Interpreter());
            assertNotNull(result);
          }
          case Program program -> {
            var env = new Environment();
            var prints = new ByteArrayOutputStream();
            var output = new PrintStream(prints, true);
            new Interpreter(env, output).interpret(program, Assertions::assertNotNull);
            assertEquals("", prints.toString(StandardCharsets.UTF_8));
          }
        }
      }
    }
  }

  @Test
  void testFunctionCall() {
    assertPrints("""
fun sayHi(first, last) {
  print "Hi, " + first + " " + last + "!";
}

sayHi("Dear", "Reader");
            """, "Hi, Dear Reader!\n");
  }

  @Test
  void testFunctionNoReturn() {
    assertPrints("""
fun procedure() {
  print "don't return anything";
}

var result = procedure();
print result;
            """, "don't return anything\nnil\n");
  }

  @Test
  void testHigherOrderFunctionInlineCall() {
    assertPrints("""
fun thrice(fn) {
  for (var i = 1; i <= 3; i = i + 1) {
    fn(i);
  }
}

thrice(fun (a) {
  print a;
});
""", "1\n2\n3\n");
  }

  void assertPrints(String input, String stdOut) {
    switch(new Scanner(input).scanTokens()) {
      case Scanner.LexError lexError -> {
        fail(lexError.getMessage());
      }
      case Scanner.TokenList tokens -> {
        switch(new Parser(tokens).parse()) {
          case ParseError parseError -> {
            fail(parseError.getMessage());
          }
          case Expr expr -> {
            var result = expr.accept(new Interpreter());
            fail("Expected a program of statements, but received the expression " + expr + " which evaluated to " + Interpreter.stringify(result));
          }
          case Program program -> {
            var env = new Environment();
            var prints = new ByteArrayOutputStream();
            var output = new PrintStream(prints, true);
            new Interpreter(env, output).interpret(program, Assertions::assertNull);
            assertEquals(stdOut, prints.toString(StandardCharsets.UTF_8));
          }
        }
      }
    }
  }

}