package com.craftinginterpreters.lox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class Lox {
  static final Interpreter INTERPRETER = new Interpreter();
  static boolean hadError = false;
  private static boolean hadRuntimeError = false;
  private static final Pattern DIRECTIVE = Pattern.compile("^(?<directive>:\\w+)");

  private enum Mode { TOKENS, PARSE_TREE, EVALUATE; }

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
    run(new String(bytes, Charset.defaultCharset()), EnumSet.of(Mode.EVALUATE));
    // Indicate an error in the exit code.
    if (hadError) System.exit(65);
    if (hadRuntimeError) System.exit(70);
  }

  private static void runPrompt() throws IOException {
    InputStreamReader input = new InputStreamReader(System.in);
    BufferedReader reader = new BufferedReader(input);

    String lastLine = "";
    Set<Mode> lastModes = EnumSet.noneOf(Mode.class);
    while (true) {
      System.out.print("> ");
      String line = reader.readLine();
      if (line == null || line.isBlank() || line.trim().equals(":exit")) break;
      var lineAndModes = directive(line, lastLine, lastModes);
      lastLine = lineAndModes.line == lastLine ? lastLine : lineAndModes.line;
      lastModes = lineAndModes.modes == lastModes ? lastModes : lineAndModes.modes;
      run(lineAndModes.line, lineAndModes.modes);
      hadError = false;
    }
  }

  private static void run(String source, Set<Mode> modes) {
    Scanner scanner = new Scanner(source);
    List<Token> tokens = scanner.scanTokens();
    if (modes.contains(Mode.TOKENS)) {
      for (Token token: tokens) {
        System.out.println(token);
      }
    }

    Parser parser = new Parser(tokens);
    ParseResult parse = parser.parse();

    if (modes.contains(Mode.PARSE_TREE) && modes.contains(Mode.TOKENS)) {
      System.out.println("\n\n");
    }

    switch (parse) {
      case Expr expression -> {
        if (modes.contains(Mode.PARSE_TREE)) {
          System.out.println(expression.accept(new AstPrinter()));
        }
        if (modes.contains(Mode.EVALUATE)) {
          try {
            var result = expression.accept(INTERPRETER);
            if (modes.size() > 1) {
              System.out.print("\n\n--[evaluates to]--> ");
            }
            System.out.println(Interpreter.stringify(result));
          } catch (EvaluationError e) {
            Lox.runtimeError(e);
          }
        }
      }
      case ParseError e -> System.err.println("Failed to parse at " + e.token() + " due to " + e.message());
    }
  }

  private record LineAndModes(String line, Set<Mode> modes){}
  private static LineAndModes directive(String line, String lastLine, Set<Mode> lastModes) {
    var match = DIRECTIVE.matcher(line);
    if (!match.find()) {
      return new LineAndModes(line, lastModes);
    }

    String directive = match.group("directive");

    var modes = switch (directive) {
      case ":lex", ":tokens" -> EnumSet.of(Mode.TOKENS);
      case ":ast", ":tree", ":parse" -> EnumSet.of(Mode.PARSE_TREE);
      case ":eval" -> EnumSet.of(Mode.EVALUATE);
      case ":all" -> EnumSet.allOf(Mode.class);
      default -> throw new EvaluationError(
              new Token(TokenType.INVALID, null, null, 0, 0),
              "Unknown directive " + directive
      );
    };

    var evalLine = line.trim().equals(directive)
            ? lastLine
            : line.replaceFirst(directive, "");

    return new LineAndModes(evalLine, modes);
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