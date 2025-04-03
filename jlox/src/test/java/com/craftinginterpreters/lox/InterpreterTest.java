package com.craftinginterpreters.lox;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.stream.Collectors;

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
            var env = new EnvironmentSimple();
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

  @Test
  void testVariableAssignment() {
    assertPrints("""
    var x = 1;
    print x;
    """, "1\n");
  }

  @Test
  void testVariableScope() {
    assertPrints("""
    var x = 1;
    fun closure() {
      print x;
    }
    closure();
    """, "1\n");
  }

  @Test
  void testVariableAssignmentInBlocksIsFastLookup() {
    assertPrints("""
    var x;
    {
      x = 3;
      print x;
    }
    """, "3\n");
  }

  @Test
  void testClosureVariableScope() {
    assertPrints("""
    fun outer() {
      var x = 1;
      fun closure() {
        print x;
      }
      return closure;
    }
    outer()();
    """, "1\n");
  }

  @Test
  void testClosureNameAsAssignment() {
    assertPrints("""
    var test = fun named() {
      print test;
      print named;
      print test == named;
    };
    test();
    """, "<fn named>\n<fn named>\ntrue\n");
  }

  @Test
  void testClosureNameAsRecursion() {
    assertPrints("""
    var test = fun recursive(i) {
      if (i == 0) { print 0; return; }
      print i;
      recursive(i - 1);
    };
    test(3);
    """, "3\n2\n1\n0\n");
  }

  @Test
  void testStatementRecursion() {
    assertPrints("""
    fun recursive(i) {
      if (i == 0) { print 0; return; }
      print i;
      recursive(i - 1);
    }
    recursive(3);
    """, "3\n2\n1\n0\n");
  }

  @Test
  void testClassDeclarationSucceeds() {
    assertPrints("""
  class Test {}
  print Test;
  """, "<class Test>\n");
  }

  @Test
  void testClassInstantiationSucceeds() {
    assertPrints("""
    class Bagel {}
    var bagel = Bagel();
    print bagel;
    """, "<Bagel instance>\n");
  }

  @Test
  void testClassMethodsCanBeInvoked() {
    assertPrints("""
    class Bacon {
      eat() {
        print "Crunch, crunch, crunch!";
      }
    }

    Bacon().eat();
    """, "Crunch, crunch, crunch!\n");
  }

  @Test
  void testClassMethodsLookupInstanceThis() {
    assertPrints("""
    class Cake {
      taste() {
        var adjective = "delicious";
        print "The " + this.flavor + " cake is " + adjective + "!";
      }
    }

    var cake = Cake();
    cake.flavor = "German chocolate";
    cake.taste();
    """, "The German chocolate cake is delicious!\n");
  }

  @Test
  void testThisLookupWorksForClosures() {
    assertPrints("""
    class Thing {
      getCallback() {
        fun localFunction() {
          print this;
        }

        return localFunction;
      }
    }
    
    var callback = Thing().getCallback();
    callback();

    var callback2 = Thing().getCallback;
    callback2()();
    """, "<Thing instance>\n<Thing instance>\n");
  }

  @Test
  void testThatInitIsCalled() {
    assertPrints("""
    class Thing {
      init(x, y) {
        print this;
        print x;
        print y;
      }
    }
    Thing(1, 2);
    """, "<Thing instance>\n1\n2\n");
  }

  @Test
  void testThisWorksInMethods() {
    assertPrints("""
    class Thing {
      init(x, y) {
        this.x = x;
        this.y = y;
        this.z = this.x + this.y;

        print this.x + " " + this.y + " " + this.z;
      }
    }
    Thing(1, 2);
    """, "1 2 3\n");
  }

  @Test
  void testGettersWork() {
    assertPrints("""
    class Circle {
      init(radius) {
        this.radius = radius;
      }

      area {
        return 3.141592653 * this.radius * this.radius;
      }
    }

    var circle = Circle(4);
    print circle.area;
    """, "50.265482448\n");
  }

  @Test
  void testClassMethodsWork() {
    assertPrints("""
    class Math {
      class init() {
        this.x = 1;
        this.y = 2;
      }
      class getter {
        return this.x + this.y;
      }
      class square(n) {
        return n * n;
      }

      init() {
        this.x = 3;
        this.y = 4;
      }

      getter {
        return this.x + this.y;
      }

      square() {
        return this.y * this.y;
      }
    }

    print Math.square(3);
    print Math.x + " " + Math.y + " " + Math.getter;

    var m = Math();
    print m.square();
    print m.x + " " + m.y + " " + m.getter;
    """, "9\n1 2 3\n16\n3 4 7\n");
  }

  @Test
  void testInheritanceWorks() {
    assertPrints("""
    class Doughnut {
      cook() {
        print "Fry until golden brown.";
      }
    }
    
    class BostonCream < Doughnut {}
    
    BostonCream().cook();
    """, "Fry until golden brown.\n");
  }

  @Test
  void testSuperWorks() {
    assertPrints("""
    class Doughnut {
      cook() {
        print "Fry until golden brown.";
      }
    }

    class BostonCream < Doughnut {
      cook() {
        super.cook();
        print "Pipe full of custard and coat with chocolate.";
      }
    }

    BostonCream().cook();
    """, "Fry until golden brown.\nPipe full of custard and coat with chocolate.\n");
  }

  void assertPrints(String input, String stdOut) {
    switch(new Scanner(input).scanTokens()) {
      case Scanner.LexError lexError -> {
        fail(lexError.getMessage());
      }
      case Scanner.TokenList tokens -> {
        switch(new Parser(tokens).parse()) {
          case ParseError parseError -> {
            var l = new ArrayList<String>();
            while (parseError != null) {
              l.add(parseError.getMessage());
              parseError = parseError.earlierError();
            }
            fail(String.join("\n", l));
          }
          case Expr expr -> {
            var result = expr.accept(new Interpreter());
            fail("Expected a program of statements, but received the expression " + expr + " which evaluated to " + Interpreter.stringify(result));
          }
          case Program program -> {
            Environment env = new EnvironmentSimple();
            var prints = new ByteArrayOutputStream();
            var output = new PrintStream(prints, true);
            var resolver = new Resolver();
            var report = resolver.resolve(program);
            if (report.hasErrors()) {
              fail(report.errors().stream().map(Resolver.ResolutionError::toString).collect(Collectors.joining("\n")));
            } else {
              env = new EnvironmentOptimized(report.locals());
            }
            var interpreter = new Interpreter(env, output);
            interpreter.interpret(program);
            interpreter.printStats();
            assertEquals(stdOut, prints.toString(StandardCharsets.UTF_8));
          }
        }
      }
    }
  }

}