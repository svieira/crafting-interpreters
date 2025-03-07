package com.craftinginterpreters.lox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;
import com.craftinginterpreters.lox.Lox.RunResults.*;

public class Lox {
  static final Interpreter INTERPRETER = new Interpreter();
  private static final Pattern DIRECTIVE = Pattern.compile("^(?<directive>(?::\\w+)+)");

  public static void main(String[] args) throws IOException {
    if (args.length > 3) {
      System.out.println("Usage: jlox [script [--mode lex | ast | eval]]");
      System.exit(64);
    } else if (args.length > 0) {
      System.out.println("Running file " + args[0]);
      runFile(args[0], args.length == 3 ? args[2] : "eval");
    } else {
      runPrompt();
    }
  }

  private static void runFile(String path, String mode) throws IOException {
    byte[] bytes = "-".equals(path.trim()) ? System.in.readAllBytes() : Files.readAllBytes(Paths.get(path));
    var directive = mode.startsWith(":") ? mode : ":" + mode;
    var modes = Mode.parse(directive).orElse(EnumSet.of(Mode.EVALUATE));
    switch(run(new String(bytes, Charset.defaultCharset()), modes)) {
      case Failure f -> {
        switch (f) {
          case LexFailure l -> {
            error(l.lexError.getLine(), l.lexError.getColumn(), l.lexError.getMessage());
            System.exit(65);
          }
          case ParseFailure p -> {
            error(p.parseError.token(), p.parseError.message());
            System.exit(65);
          }
          case ResolutionFailure r -> {
            for (var ex : r.report.errors()) {
              error(ex.token(), ex.message());
            }
          }
          case EvalFailure e -> {
            runtimeError(e.evalError);
            System.exit(70);
          }
        }
      }
      case Success s -> {
        displaySuccess(s, modes);
        System.exit(0);
      }
    }
  }

  private static void runPrompt() throws IOException {
    InputStreamReader input = new InputStreamReader(System.in);
    BufferedReader reader = new BufferedReader(input);

    var lastScript = "";
    var isContinuationLine = false;
    Set<Mode> lastModes = EnumSet.of(Mode.EVALUATE);
    var reportParseError = false;
    loop: while (true) {
      prompt(lastModes, isContinuationLine);
      String line = reader.readLine();
      line = line == null ? "" : line.trim();

      switch (line) {
        case ":report" -> {
          reportParseError = !reportParseError;
          line = "";
        }
        case ":reset" -> {
          line = "";
          lastScript = "";
          isContinuationLine = false;
        }
        case ":exit" -> {
          break loop;
        }
      }

      var lineAndModes = directive(line, lastScript, lastModes);
      // If we reused the line then we have a mode change by itself.
      lastScript = lineAndModes.script;
      lastModes = lineAndModes.modes;

      switch (run(lineAndModes.script, lineAndModes.modes)) {
        case Success s -> {
          lastScript = "";
          isContinuationLine = false;
          displaySuccess(s, lastModes);
        }
        case EvalFailure e -> {
          lastScript = "";
          isContinuationLine = false;
          runtimeError(e.evalError);
        }
        case LexFailure f -> {
          lastScript = "";
          isContinuationLine = false;
          error(f.lexError.getLine(), f.lexError.getColumn(), f.lexError.getMessage());
        }
        case ResolutionFailure r -> {
          lastScript = "";
          isContinuationLine = false;
          for (var ex : r.report.errors()) {
            error(ex.token(), ex.message());
          }
        }
        case ParseFailure p -> {
          if (reportParseError) {
            error(p.parseError.token(), p.parseError.message());
            if (p.parseError.earlierError() != null) {
              error(p.parseError.earlierError().token(), p.parseError.earlierError().message());
            }
          }
          isContinuationLine = true;
        }
      }
    }
  }

  private static void prompt(Set<Mode> lastModes, boolean continuationLine) {
    if (continuationLine) {
      System.out.print("  ");
      return;
    }
    var modes = new StringBuilder();
    for (var mode : lastModes) {
      modes.append(switch (mode) {
        case EVALUATE -> ":eval";
        case PARSE_TREE -> ":ast";
        case TOKENS -> ":lex";
      });
    }
    var modals = modes.toString();
    System.out.print((modals.equals(":eval") ? "" : modals) + "> ");
  }

  private static RunResults run(String source, Set<Mode> modes) {
    Scanner scanner = new Scanner(source);
    Scanner.ScanResults results = scanner.scanTokens();
    return switch (results) {
      case Scanner.TokenList tokens -> {
        if (modes.size() == 1 && modes.contains(Mode.TOKENS)) {
          yield new LexSuccess(tokens);
        }
        yield parseAndRun(tokens, modes);
      }
      case Scanner.LexError lexError -> new LexFailure(lexError);
    };
  }

  sealed interface RunResults {
    sealed interface Success extends RunResults {
      Scanner.TokenList lex();
    }
    record LexSuccess(Scanner.TokenList lex) implements Success {}
    record ParseSuccess(Scanner.TokenList lex, ParseResult.Success parse) implements Success {}
    record ExpressionSuccess(Scanner.TokenList lex, Expr expression, Object result) implements Success {}
    record ProgramSuccess(Scanner.TokenList lex, Program program) implements Success {}

    sealed interface Failure extends RunResults {}
    record LexFailure(Scanner.LexError lexError) implements Failure {}
    record ParseFailure(ParseError parseError) implements Failure {}
    record ResolutionFailure(Resolver.ResolutionReport report) implements Failure {}
    record EvalFailure(EvaluationError evalError) implements Failure {}
  }


  private enum Mode {
    TOKENS, PARSE_TREE, EVALUATE;
    static Optional<EnumSet<Mode>> parse(String directive) {
      EnumSet<Mode> modes = EnumSet.noneOf(Mode.class);
      for (var d : directive.split(":")) {
        d = ":" + d.toLowerCase().trim();
        modes.addAll(parseInner(d));
      }
      return modes.isEmpty() ? Optional.empty() : Optional.of(modes);
    }
    static EnumSet<Mode> parseInner(String directive) {
      return switch (directive) {
        case ":lex", ":tokens" -> EnumSet.of(Mode.TOKENS);
        case ":ast", ":tree", ":parse" -> EnumSet.of(Mode.PARSE_TREE);
        case ":eval", ":exec" -> EnumSet.of(Mode.EVALUATE);
        case ":all" -> EnumSet.allOf(Mode.class);
        default -> EnumSet.noneOf(Mode.class);
      };
    }
  }

  private static RunResults parseAndRun(Scanner.TokenList tokens, Set<Mode> modes) {
    Parser parser = new Parser(tokens);
    ParseResult parse = parser.parse();

    switch (parse) {
      case Expr expression -> {
        if (!modes.contains(Mode.EVALUATE)) {
          return new ParseSuccess(tokens, expression);
        }
        try {
          var result = expression.accept(INTERPRETER);
          return new ExpressionSuccess(tokens, expression, result);
        } catch (EvaluationError e) {
          return new EvalFailure(e);
        }
      }
      case Program program -> {
        var resolver = new Resolver(INTERPRETER);
        var report = resolver.resolve(program);
        if (report.hasErrors()) {
          return new ResolutionFailure(report);
        }

        if (!modes.contains(Mode.EVALUATE)) {
          return new ParseSuccess(tokens, program);
        }

        try {
          INTERPRETER.interpret(program);
          return new ProgramSuccess(tokens, program);
        } catch (EvaluationError e) {
          return new EvalFailure(e);
        } catch (Exception e) {
          return new EvalFailure(new EvaluationError(e));
        }
      }
      case ParseError e -> {
        return new ParseFailure(e);
      }
    }
  }

  private static void displaySuccess(Success success, Set<Mode> modes) {
    var multiMode = modes.size() > 1;
    if (modes.contains(Mode.TOKENS)) {
      displayLex(success.lex());
    }

    if (multiMode) System.out.println();

    if (modes.contains(Mode.PARSE_TREE)) {
      switch (success) {
        case LexSuccess lex -> {/* ignored */}
        case ParseSuccess parse -> {
          switch (parse.parse) {
            case Expr expression -> displayExpression(expression);
            case Program program -> displayProgram(program);
          }
        }
        case ExpressionSuccess e -> displayExpression(e.expression);
        case ProgramSuccess p -> displayProgram(p.program);
      }
    }

    if (multiMode) System.out.println();

    if (modes.contains(Mode.EVALUATE)) {
      if (success instanceof ExpressionSuccess expressionSuccess) {
        System.out.println(Interpreter.stringify(expressionSuccess.result));
      }
      System.out.println();
    }
  }

  private static void displayProgram(Program program) {
    program.accept(new AstPrinter()).forEach(System.out::println);
  }

  private static void displayExpression(Expr expression) {
    System.out.println(expression.accept(new AstPrinter()));
  }

  private static void displayLex(Scanner.TokenList tokens) {
    for (Token token: tokens) {
      System.out.println(token);
    }
  }

  private record LineAndModes(String script, Set<Mode> modes){}
  private static LineAndModes directive(String line, String previousScript, Set<Mode> lastModes) {
    var match = DIRECTIVE.matcher(line);
    if (!match.find()) {
      return new LineAndModes(previousScript + '\n' + line, lastModes);
    }

    String directive = match.group("directive");

    var modes = Mode.parse(directive).orElseThrow(() ->
      new EvaluationError(
            new Token(TokenType.INVALID, null, null, 0, 0),
            "Unknown directive " + directive
      )
    );

    var newScript = line.trim().equals(directive)
            ? previousScript
            // Only allowing mode changes with expressions, not in the middle of statements
            : line.replaceFirst(directive, "");

    return new LineAndModes(newScript, modes);
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
  }

  private static void report(int line, int column, String where, String message) {
    System.err.println("[line " + line + "][column " + column + "] Error" + where + ": " + message);
  }
}