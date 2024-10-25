package com.craftinginterpreters.lox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Lox {
  static boolean hadError = false;

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
    ParseResult result = parser.parse();
    switch (result) {
      case Expr expression -> System.out.println(new AstPrinter().print(expression));
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

  private static void report(int line, int column, String where, String message) {
    System.err.println("[line " + line + "][column " + column + "] Error" + where + ": " + message);
    hadError = true;
  }
}