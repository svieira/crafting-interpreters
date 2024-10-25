package com.craftinginterpreters.lox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Lox {
  public static final Interpreter INTERPRETER = new Interpreter();
  static boolean hadError = false;
  private static boolean hadRuntimeError = false;

  public static void main(String[] args) throws IOException {
    if (args.length > 1) {
      System.out.println("Usage: jlox [script]");
      System.exit(64);
    } else if (args.length == 1) {
      runFile(args[0]);
    } else {
      runPrompt();
    }
  }

  private static void runFile(String path) throws IOException {
    byte[] bytes = Files.readAllBytes(Paths.get(path));
    run(new String(bytes, Charset.defaultCharset()));
    // Indicate an error in the exit code.
    if (hadError) System.exit(65);
    if (hadRuntimeError) System.exit(70);
  }

  private static void runPrompt() throws IOException {
    InputStreamReader input = new InputStreamReader(System.in);
    BufferedReader reader = new BufferedReader(input);

    while (true) {
      System.out.print("> ");
      String line = reader.readLine();
      if (line == null) break;
      run(line);
      hadError = false;
    }
  }

  private static void run(String source) {
    Scanner scanner = new Scanner(source);
    List<Token> tokens = scanner.scanTokens();

    System.out.println("Tokens:");
    // For now, just print the tokens.
    for (Token token: tokens) {
      System.out.println(token);
    }

    System.out.println("\n\nAST:");
    Parser parser = new Parser(tokens);
    ParseResult parse = parser.parse();
    switch (parse) {
      case Expr expression -> {
        System.out.println(expression.accept(new AstPrinter()));
        try {
          var result = expression.accept(INTERPRETER);
          System.out.println(" --[evals to]--> " + Interpreter.stringify(result));
        } catch (EvaluationError e) {
          Lox.runtimeError(e);
        }
      }
      case ParseError e -> System.err.println("Failed to parse at " + e.token() + " due to " + e.message());
    }
  }

  static void error(int line, int column, String message) {
    report(line, column, "", message);
  }

  static void error(Token token, String message) {
    if (token.type() == TokenType.EOF) {
      report(token.line(), token.column()," at end", message);
    } else {
      report(token.line(), token.column(), " at '" + token.lexeme() + "'", message);
    }
  }

  static void runtimeError(EvaluationError error) {
    System.err.println(error.getMessage() +
            "\n[" + error.getToken().line() + ":" + error.getToken().column() + "]");
    hadRuntimeError = true;
  }

  private static void report(int line, int column, String where, String message) {
    System.err.println("[line " + line + "][column " + column + "] Error" + where + ": " + message);
    hadError = true;
  }
}