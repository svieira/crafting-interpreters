package com.craftinginterpreters.lox;

import java.io.PrintStream;
import java.util.*;
import java.util.function.Consumer;

import static com.craftinginterpreters.lox.TokenType.SUPER;
import static com.craftinginterpreters.lox.TokenType.THIS;

public class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {
  private final Environment environment;
  private final PrintStream printTarget;

  public Interpreter() {
    this(new EnvironmentSimple(new EnvironmentGlobal()), System.out);
  }

  Interpreter(Environment environment) {
    this(environment, System.out);
  }

  Interpreter(Environment environment, PrintStream printTarget) {
    this.environment = environment;
    this.printTarget = printTarget;
  }

  void interpret(Program program) {
    interpret(program, e -> {
      throw e;
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
  public Void visit(Stmt.ClassDeclaration classDeclaration) {
    LoxClass superclass = null;
    if (classDeclaration.superclass() != null) {
      var maybeSuperclass = evaluate(classDeclaration.superclass());
      if (!(maybeSuperclass instanceof LoxClass)) {
        throw new EvaluationError(classDeclaration.superclass().name(), "Superclass must be a class");
      }
      superclass = (LoxClass) maybeSuperclass;
    }

    environment.define(classDeclaration.name(), null);
    var env = environment;
    if (superclass != null) {
      env = env.pushScope();
      env.define(Token.artificial(SUPER), superclass);
    }

    var methods = new HashMap<String, LoxFunction>(classDeclaration.methods().size());
    for (var method : classDeclaration.methods()) {
      methods.put(method.name().lexeme(), LoxFunction.method(method, env));
    }
    var classMethods = new HashMap<String, LoxFunction>(classDeclaration.classMethods().size());
    for (var method : classDeclaration.classMethods()) {
      classMethods.put(method.name().lexeme(), LoxFunction.method(method, env));
    }
    var klass = new LoxClass(classDeclaration.name().lexeme(), superclass, methods, classMethods);
    klass.initialize(this);
    environment.assign(classDeclaration.name(), klass);
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
    executeBlock(block.statements(), environment.pushScope());
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
  public Object visit(Expr.Select select) {
    var target = evaluate(select.target());
    if (target instanceof LoxInstance instance) {
      Object value = instance.get(select.field());
      if (value instanceof LoxFunction function) {
        if (function.isGetter()) {
          return function.call(this, List.of());
        }
      }
      return value;
    }
    throw new EvaluationError(select.field(), "Only instances have properties");
  }

  @Override
  public Object visit(Expr.Update update) {
    var target = evaluate(update.target());
    if (target instanceof LoxInstance instance) {
      var value = evaluate(update.value());
      instance.set(update.field(), value);
      return value;
    }
    throw new EvaluationError(update.field(), "Only instances have fields");
  }

  @Override
  public Object visit(Expr.Function f) {
    var fun = new Stmt.Function(f.name(), f.arguments(), f.body());
    var env = environment.pushScope();
    var func = new LoxFunction(fun, env);
    if (!f.isAnonymous()) {
      env.define(f.name(), func);
    }
    return func;
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
    return lookupVariable(variable.name());
  }

  @Override
  public Object visit(Expr.This the) {
    return lookupVariable(the.keyword());
  }

  @Override
  public Object visit(Expr.Super superCall) {
    var instanceEnv = environment.getEnvironmentOf(Token.artificial(THIS));
    // HACK: This relies on the environment layout we build in LoxFunction#bind and Interpreter#visit(Stmt.ClassDeclaration)
    LoxClass superClass = (LoxClass)instanceEnv.parent().get(superCall.keyword());
    LoxInstance loxObject = (LoxInstance) instanceEnv.get(Token.artificial(THIS));
    LoxFunction method = superClass.findMethod(superCall.method().lexeme());
    if (method == null) {
      throw new EvaluationError(superCall.method(), "Undefined method " + superCall.method().lexeme());
    }
    return method.bind(loxObject);
  }

  // Language semantics and operations!
  private Object lookupVariable(Token name) {
    return environment.get(name);
  }

  void printStats() {
    environment.printStats();
  }

  private Object evaluate(Expr expression) {
    return expression.accept(this);
  }

  private void execute(Stmt stmt) {
    stmt.accept(this);
  }

  void executeBlock(List<Stmt> statements, Environment environment) {
    // Look ma, no mutation!
    // Yes child, but that's a lot of allocation!
    var blockFrame = new Interpreter(environment, printTarget);
    for (Stmt statement : statements) {
      statement.accept(blockFrame);
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