package com.craftinginterpreters.lox;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {
  private final Environment environment;

  public Interpreter() {
    this(new Environment());
  }

  Interpreter(Environment environment) {
    this.environment = environment;
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

    environment.define(stmt.name().lexeme(), value);
    return null;
  }

  @Override
  public Void visit(Stmt.Print stmt) {
    Object value = evaluate(stmt.expression());
    System.out.println(stringify(value));
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
    return null;
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

  private void executeBlock(List<Stmt> statements, Environment environment) {
    for (Stmt statement : statements) {
      // Look ma, no mutation!
      // Yes child, but that's a lot of allocation!
      statement.accept(new Interpreter(environment));
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
}