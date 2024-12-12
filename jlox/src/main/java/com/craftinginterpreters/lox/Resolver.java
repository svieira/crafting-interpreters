package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {
  private final Interpreter interpreter;
  private final Stack<Map<String, Boolean>> scopes = new Stack<>();

  Resolver(Interpreter interpreter) {
    this.interpreter = interpreter;
    this.scopes.push(new HashMap<>()); // The top-level scope
  }

  @Override
  public Void visit(Stmt.Block stmt) {
    beginScope();
    resolve(stmt.statements());
    endScope();
    return null;
  }

  @Override
  public Void visit(Stmt.Var declaration) {
    declare(declaration.name());
    if (declaration.initializer() != null) {
      resolve(declaration.initializer());
    }
    define(declaration.name());
    return null;
  }

  @Override
  public Void visit(Expr.Variable variable) {
    if (!scopes.isEmpty() &&
            scopes.peek().get(variable.name().lexeme()) == Boolean.FALSE) {
      Lox.error(variable.name(),
              "Can't read local variable in its own initializer.");
    }

    resolveLocal(variable, variable.name());
    return null;
  }

  @Override
  public Void visit(Expr.Assignment assignment) {
    resolve(assignment.value());
    resolveLocal(assignment, assignment.name());
    return null;
  }

  @Override
  public Void visit(Stmt.Function function) {
    declare(function.name());
    define(function.name());

    resolveFunction(function);
    return null;
  }

  @Override
  public Void visit(Expr.Function function) {
    resolveFunction(function);
    return null;
  }

  @Override
  public Void visit(Stmt.Expression expression) {
    resolve(expression.expression());
    return null;
  }

  @Override
  public Void visit(Stmt.If anIf) {
    resolve(anIf.condition());
    resolve(anIf.whenTrue());
    if (anIf.whenFalse() != null) resolve(anIf.whenFalse());
    return null;
  }

  @Override
  public Void visit(Stmt.Print print) {
    resolve(print.expression());
    return null;
  }

  @Override
  public Void visit(Stmt.Return returnStmt) {
    if (returnStmt.value() != null) resolve(returnStmt.value());
    return null;
  }

  @Override
  public Void visit(Stmt.LoopControl loopControl) {
    return null;
  }

  @Override
  public Void visit(Stmt.While aWhile) {
    resolve(aWhile.condition());
    resolve(aWhile.body());
    return null;
  }

  @Override
  public Void visit(Expr.Trinary trinary) {
    resolve(trinary.head());
    resolve(trinary.left());
    resolve(trinary.right());
    return null;
  }

  @Override
  public Void visit(Expr.Binary binary) {
    resolve(binary.left());
    resolve(binary.right());
    return null;
  }

  @Override
  public Void visit(Expr.Logical logical) {
    resolve(logical.left());
    resolve(logical.right());
    return null;
  }

  @Override
  public Void visit(Expr.Unary unary) {
    resolve(unary.right());
    return null;
  }

  @Override
  public Void visit(Expr.Grouping grouping) {
    resolve(grouping.expression());
    return null;
  }

  @Override
  public Void visit(Expr.Literal literal) {
    return null;
  }

  @Override
  public Void visit(Expr.Call call) {
    resolve(call.callee());
    for (Expr arg : call.arguments()) {
      resolve(arg);
    }
    return null;
  }

  private void resolveLocal(Expr expr, Token name) {
    for (int i = scopes.size() - 1; i >= 0; i--) {
      if (scopes.get(i).containsKey(name.lexeme())) {
        interpreter.resolve(expr, scopes.size() - 1 - i);
        return;
      }
    }
  }

  private void declare(Token name) {
    if (scopes.isEmpty()) return;

    Map<String, Boolean> scope = scopes.peek();
    if (scope.containsKey(name.lexeme())) {
      Lox.error(name, "Already a variable with this name in this scope.");
    }
    scope.put(name.lexeme(), false);
  }

  private void define(Token name) {
    if (scopes.isEmpty()) return;
    scopes.peek().put(name.lexeme(), true);
  }

  void resolve(List<Stmt> statements) {
    for (Stmt statement : statements) {
      resolve(statement);
    }
  }

  private void resolve(Stmt statement) {
    statement.accept(this);
  }

  private void resolve(Expr expression) {
    expression.accept(this);
  }

  private void resolveFunction(Stmt.Function function) {
    beginScope();
    for (Token param : function.params()) {
      declare(param);
      define(param);
    }
    resolve(function.body());
    endScope();
  }

  private void resolveFunction(Expr.Function function) {
    beginScope();
    if (!function.isAnonymous()) {
      declare(function.name());
      define(function.name());
    }
    for (Token param : function.arguments()) {
      declare(param);
      define(param);
    }
    resolve(function.body());
    endScope();
  }

  private void beginScope() {
    scopes.push(new HashMap<>());
  }

  private void endScope() {
    scopes.pop();
  }
}