package com.craftinginterpreters.lox;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {
  final Environment globals = new Environment();
  private final Environment environment;
  private final PrintStream printTarget;

  public Interpreter() {
    this(new Environment(), System.out);
  }

  Interpreter(Environment environment) {
    this(environment, System.out);
  }

  Interpreter(PrintStream printTarget) {
    this(new Environment(), printTarget);
  }

  Interpreter(Environment environment, PrintStream printTarget) {
    this.environment = environment;
    this.printTarget = printTarget;
    this.globals.define("clock", new LoxCallable() {
      @Override
      public Object call(Interpreter interpreter, List<Object> arguments) {
        return System.currentTimeMillis() / 1_000d;
      }

      @Override
      public int arity() {
        return 0;
      }

      @Override
      public String toString() {
        return "<native fn>";
      }
    });
  }

  void interpret(Program program, Consumer<EvaluationError> handler) {
    try {
      for (Stmt statement : program) {
        execute(statement);
      }
    } catch (EvaluationError error) {
      handler.accept(error);
    }
  }

  @Override
  public Void visit(Stmt.Var stmt) {
    Object value = null;
    if (stmt.initializer() != null) {
      value = evaluate(stmt.initializer());
    }

    environment.define(stmt.name(), value);
    return null;
  }

  @Override
  public Void visit(Stmt.If stmt) {
    if (isTruthy(evaluate(stmt.condition()))) {
      execute(stmt.whenTrue());
    } else if (stmt.whenFalse() != null) {
      execute(stmt.whenFalse());
    }
    return null;
  }

  @Override
  public Void visit(Stmt.While stmt) {
    while (isTruthy(evaluate(stmt.condition()))) {
      try {
        execute(stmt.body());
      } catch (LoopControlSignal signal) {
        if (signal.control.type() == Stmt.LoopControl.Type.BREAK) {
          break;
        } else if (signal.control.type() == Stmt.LoopControl.Type.CONTINUE) {
          continue;
        } else {
          throw new EvaluationError(signal.control.token(), "Do not know how to handle signal of type " + signal.control.type());
        }
      }
    }
    return null;
  }

  @Override
  public Void visit(Stmt.LoopControl loopControl) {
    throw new LoopControlSignal(loopControl);
  }

  @Override
  public Void visit(Stmt.Function function) {
    var f = new LoxFunction(function, environment);
    environment.define(function.name(), f);
    return null;
  }

  @Override
  public Void visit(Stmt.Return returnStmt) {
    var expr = returnStmt.value();
    Object value = null;
    if (expr != null) {
      value = evaluate(expr);
    }
    throw new ReturnSignal(value);
  }

  @Override
  public Void visit(Stmt.Print stmt) {
    Object value = evaluate(stmt.expression());
    printTarget.println(stringify(value));
    return null;
  }

  @Override
  public Void visit(Stmt.Block block) {
    executeBlock(block.statements(), new Environment(environment));
    return null;
  }

  @Override
  public Void visit(Stmt.Expression stmt) {
    evaluate(stmt.expression());
    return null;
  }

  @Override
  public Object visit(Expr.Assignment assignment) {
    var result = evaluate(assignment.value());
    environment.assign(assignment.name(), result);
    return result;
  }

  @Override
  public Object visit(Expr.Logical logical) {
    Object left = evaluate(logical.left());
    boolean isOr = logical.operator().type() == TokenType.OR;
    if (isOr) {
      return isTruthy(left) ? left : evaluate(logical.right());
    }
    return !isTruthy(left) ? left : evaluate(logical.right());
  }

  @Override
  public Object visit(Expr.Trinary trinary) {
    Object test = evaluate(trinary.head());
    Expr branch = isTruthy(test) ? trinary.left() : trinary.right();
    return evaluate(branch);
  }

  @Override
  public Object visit(Expr.Binary binary) {
    Object left = evaluate(binary.left());
    var opType = binary.operator().type();
    if (opType == TokenType.ELVIS) {
      return isTruthy(left) ? left : evaluate(binary.right());
    }

    Object right = evaluate(binary.right());

    if (opType == TokenType.EQUAL_EQUAL) {
      return isEquals(left, right);
    }
    if (opType == TokenType.BANG_EQUAL) {
      return !isEquals(left, right);
    }

    if (left instanceof Double l && right instanceof Double r) {
      return switch (opType) {
        case MINUS -> l - r;
        case SLASH -> {
          require(r != 0, binary.operator(), "Division by zero");
          yield l / r;
        }
        case STAR -> l * r;
        case PLUS -> l + r;
        case GREATER -> l > r;
        case GREATER_EQUAL -> l >= r;
        case LESS -> l < r;
        case LESS_EQUAL -> l <= r;
        default -> fail(
                binary.operator(),
                "Do not know how to apply " + opType + " to numbers"
        );
      };
    } else if (left instanceof String || right instanceof String) {
      require(
        opType == TokenType.PLUS,
        binary.operator(),
        "Do not know how to apply " + opType + " to strings"
      );
      return stringify(left) + stringify(right);
    }

    return fail(binary.operator(), "Operands for " + opType + " must be numbers or strings");
  }

  @Override
  public Object visit(Expr.Unary unary) {
    TokenType opType = unary.operator().type();
    var isMinus = opType == TokenType.MINUS;
    var isNegation = opType == TokenType.BANG;
    require(
      !(isMinus || isNegation),
      unary.operator(),
      "Do not know how to handle unary operator of type " + opType
    );
    var right = evaluate(unary.right());
    if (isMinus) {
      return -requireIsNumber(unary.operator(), right);
    } else if (isNegation) {
      return !isTruthy(right);
    }
    return null; // Unreachable
  }

  @Override
  public Object visit(Expr.Call call) {
    Object callee = evaluate(call.callee());

    List<Object> arguments = new ArrayList<>();
    for (Expr argument : call.arguments()) {
      arguments.add(evaluate(argument));
    }

    if (callee instanceof LoxCallable function) {
      if (function.arity() != arguments.size()) {
        throw new EvaluationError(
                call.paren(),
                "Expected " + function.arity() + " arguments but got " + arguments.size() + "."
        );
      }
      return function.call(this, arguments);
    } else {
      throw new EvaluationError(call.paren(), "Can only call functions and classes");
    }
  }

  @Override
  public Object visit(Expr.Function f) {
    var fun = new Stmt.Function(f.name(), f.arguments(), f.body());
    var env = new Environment(environment);
    env.define(f.name(), fun);
    return new LoxFunction(fun, env);
  }

  @Override
  public Object visit(Expr.Grouping grouping) {
    return evaluate(grouping.expression());
  }

  @Override
  public Object visit(Expr.Literal literal) {
    return literal.value();
  }

  @Override
  public Object visit(Expr.Variable variable) {
    return environment.get(variable.name());
  }

  // Language semantics and operations!
  private Object evaluate(Expr expression) {
    return expression.accept(this);
  }

  private void execute(Stmt stmt) {
    stmt.accept(this);
  }

  void executeBlock(List<Stmt> statements, Environment environment) {
    for (Stmt statement : statements) {
      // Look ma, no mutation!
      // Yes child, but that's a lot of allocation!
      statement.accept(new Interpreter(environment, printTarget));
    }
  }

  private boolean isTruthy(Object object) {
    if (object == null) return false;
    if (object instanceof Boolean) return (boolean)object;
    return true;
  }

  private static boolean isEquals(Object left, Object right) {
    return Objects.equals(left, right);
  }

  public static String stringify(Object value) {
    if (value == null) return "nil";

    if (value instanceof Double) {
      String text = value.toString();
      if (text.endsWith(".0")) {
        text = text.substring(0, text.length() - 2);
      }
      return text;
    }

    return value.toString();
  }

  private static double requireIsNumber(Token operator, Object operand) {
    if (operand instanceof Double value) {
      return value;
    }
    throw fail(operator, "Operand must be a number");
  }

  // Utilities
  private void require(boolean test, Token location, String failureMessage) {
    if (!test) throw fail(location, failureMessage);
  }

  private static EvaluationError fail(Token location, String failureMessage) {
    throw new EvaluationError(location, failureMessage);
  }

  private static final class LoopControlSignal extends RuntimeException {
    final Stmt.LoopControl control;

    public LoopControlSignal(Stmt.LoopControl control) {
      this.control = control;
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
      // As a signal, we never need to know where we are coming from
      // and filling in the stack trace is expensive.
      return this;
    }
  }
}