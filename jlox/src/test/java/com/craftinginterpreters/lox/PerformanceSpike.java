package com.craftinginterpreters.lox;

import java.util.EnumSet;

public class PerformanceSpike {
  public static void main(String[] args) {
    var tough = """
    fun fib(n) {
      if (n < 2) return n;
      return fib(n - 1) + fib(n - 2);
    }
    
    var before = clock();
    print "The answer to 40! is " + fib(40);
    var after = clock();
    print "This operation took " + (after - before) + " seconds to complete";
    """;
    var easy = """
    fun odd(i) {
      if (i < 2) return i;
      return odd(i - 1) + odd(i - 2);
    }
    print "The value of this operation is " + odd(3);
    """;
    try {
      switch(Lox.run(tough, EnumSet.of(Lox.Mode.EVALUATE))) {
        case Lox.RunResults.Success s -> System.out.println(s);
        case Lox.RunResults.Failure s -> System.out.println(s);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
