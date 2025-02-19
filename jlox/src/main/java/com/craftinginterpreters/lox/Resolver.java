package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {
  record Coordinates(int scope, int id) {}
  private static final class VarState {
    boolean defined;
    final int id;
    VarState(int id) {
      this.id = id;
    }

    public VarState(int id, boolean defined) {
      this.id = id;
      this.defined = defined;
    }
  }
  private static final class State {
    private Expr expr;
    private Stmt stmt;
    int id = 0;
    Map<String, VarState> variables = new HashMap<>();
    State() {}
    State(Stmt stmt) {
      this.stmt = stmt;
    }
    State(Expr expr) {
      this.expr = expr;
    }
  }

  private final Interpreter interpreter;
  private final Stack<State> scopes = new Stack<>();

  Resolver(Interpreter interpreter) {
    this.interpreter = interpreter;
    this.scopes.push(new State()); // The top-level scope
  }

  @Override
  public Void visit(Stmt.Block stmt) {
    try(var s = scope(stmt)) {
      resolve(stmt.statements());
    }
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
    if (!scopes.isEmpty()) {
      var varName = variable.name().lexeme();
      var variableReference = scopes.peek().variables.get(varName);

      if (variableReference != null && variableReference.defined == Boolean.FALSE) {
        Lox.error(variable.name(),
              "Can't read local variable in its own initializer.");
      }
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
  public Void visit(Stmt.ClassDeclaration classDeclaration) {
    declare(classDeclaration.name());
    define(classDeclaration.name());

    ScopeManager superclassScope = null;
    if (classDeclaration.superclass() != null) {
      if (classDeclaration.name().lexeme().equals(classDeclaration.superclass().name().lexeme())) {
        Lox.error(classDeclaration.name(), "Class cannot extend from itself");
      }
      resolve(classDeclaration.superclass());
      superclassScope = scope();
      define("super");
    }

    try(var s = scope(classDeclaration)) {
      declare("this", null);
      define("this");
      for (var f : classDeclaration.methods()) {
        f.accept(this);
      }
    }
    if (superclassScope != null) {
      superclassScope.close();
    }
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
  public Void visit(Expr.Select select) {
    resolve(select.target());
    return null;
  }

  @Override
  public Void visit(Expr.Update update) {
    resolve(update.target());
    resolve(update.value());
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
  public Void visit(Expr.This the) {
    resolveLocal(the, the.keyword());
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

  @Override
  public Void visit(Expr.Super superCall) {
    resolveLocal(superCall, superCall.keyword());
    return null;
  }

  private void resolveLocal(Expr expr, Token name) {
    for (int i = scopes.size() - 1; i >= 0; i--) {
      if (scopes.get(i).variables.containsKey(name.lexeme())) {
        interpreter.resolve(expr, new Coordinates(scopes.size() - 1 - i, scopes.get(i).variables.get(name.lexeme()).id));
        return;
      }
    }
  }

  private void declare(String name, Token token) {
    if (scopes.isEmpty()) return;

    var scope = scopes.peek();
    if (scope.variables.containsKey(name)) {
      Lox.error(token, "Already a variable with this name in this scope.");
    }
    scope.variables.put(name, new VarState(scope.id++));
  }
  private void declare(Token name) {
    declare(name.lexeme(), name);
  }

  private void define(String name) {
    if (scopes.isEmpty()) return;
    scopes.peek().variables.compute(name, (key,value) -> {
      if (value == null) {
        return new VarState(scopes.peek().id++, true);
      }
      value.defined = true;
      return value;
    });
  }
  private void define(Token name) {
    define(name.lexeme());
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
    try(var s = scope(function)) {
      for (Token param : function.params()) {
        declare(param);
        define(param);
      }
      resolve(function.body());
    }
  }

  private void resolveFunction(Expr.Function function) {
    try(var s = scope(function)) {
      if (!function.isAnonymous()) {
        declare(function.name());
        define(function.name());
      }
      for (Token param : function.arguments()) {
        declare(param);
        define(param);
      }
      resolve(function.body());
    }
  }

  private ScopeManager scope() {
    scopes.push(new State());
    return scopes::pop;
  }

  private ScopeManager scope(Stmt statement) {
    scopes.push(new State(statement));
    return scopes::pop;
  }

  private ScopeManager scope(Expr expression) {
    scopes.push(new State(expression));
    return scopes::pop;
  }

  private interface ScopeManager extends AutoCloseable {
    void close();
  }
}