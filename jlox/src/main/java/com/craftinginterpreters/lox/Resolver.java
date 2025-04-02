package com.craftinginterpreters.lox;

import java.util.*;

import static com.craftinginterpreters.lox.TokenType.SUPER;
import static com.craftinginterpreters.lox.TokenType.THIS;

class Resolver implements Expr.Visitor<Resolver.ResolutionReport>, Stmt.Visitor<Resolver.ResolutionReport> {
  record ResolutionReport(List<ResolutionError> errors, StatsCountingLocals locals) {
    public ResolutionReport() {
      this(new ArrayList<>(), new StatsCountingLocals());
    }

    public boolean hasErrors() {
      return !errors.isEmpty();
    }

    public void add(ResolutionError resolutionError) {
      errors.add(resolutionError);
    }
  }
  record ResolutionError(Token token, String message) {}

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
    private Expr expr; // For debugging
    private Stmt stmt; // For debugging
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

  private final Stack<State> scopes = new Stack<>();
  private final ResolutionReport report = new ResolutionReport();

  Resolver() {
    this.scopes.push(new State()); // The top-level scope
  }

  ResolutionReport resolve(List<Stmt> statements) {
    for (Stmt statement : statements) {
      resolve(statement);
    }
    return report;
  }

  @Override
  public ResolutionReport visit(Stmt.Block stmt) {
    try(var s = scope(stmt)) {
      return resolve(stmt.statements());
    }
  }

  @Override
  public ResolutionReport visit(Stmt.Var declaration) {
    declare(declaration.name());
    if (declaration.initializer() != null) {
      resolve(declaration.initializer());
    }
    define(declaration.name());
    resolveLocal(declaration.name());
    return report;
  }

  @Override
  public ResolutionReport visit(Expr.Variable variable) {
    if (!scopes.isEmpty()) {
      var varName = variable.name().lexeme();
      var variableReference = scopes.peek().variables.get(varName);

      if (variableReference != null && variableReference.defined == Boolean.FALSE) {
        report.add(new ResolutionError(variable.name(),
              "Can't read local variable in its own initializer."));
      }
    }

    resolveLocal(variable.name());
    return report;
  }

  @Override
  public ResolutionReport visit(Expr.Assignment assignment) {
    resolve(assignment.value());
    resolveLocal(assignment.name());
    return report;
  }

  @Override
  public ResolutionReport visit(Stmt.Function function) {
    declare(function.name());
    define(function.name());

    resolveFunction(function);
    return report;
  }

  @Override
  public ResolutionReport visit(Stmt.ClassDeclaration classDeclaration) {
    declare(classDeclaration.name());
    define(classDeclaration.name());

    ScopeManager superclassScope = null;
    if (classDeclaration.superclass() != null) {
      if (classDeclaration.name().lexeme().equals(classDeclaration.superclass().name().lexeme())) {
        report.add(new ResolutionError(classDeclaration.name(), "Class cannot extend from itself"));
      }
      resolve(classDeclaration.superclass());
      superclassScope = scope();
      define(Token.artificial(SUPER));
    }

    try(var s = scope(classDeclaration)) {
      declare(THIS.keyword(), Token.artificial(THIS));
      define(Token.artificial(THIS));
      for (var f : classDeclaration.methods()) {
        f.accept(this);
      }
    }
    if (superclassScope != null) {
      superclassScope.close();
    }
    return report;
  }

  @Override
  public ResolutionReport visit(Expr.Function function) {
    resolveFunction(function);
    return report;
  }

  @Override
  public ResolutionReport visit(Stmt.Expression expression) {
    resolve(expression.expression());
    return report;
  }

  @Override
  public ResolutionReport visit(Expr.Select select) {
    resolve(select.target());
    return report;
  }

  @Override
  public ResolutionReport visit(Expr.Update update) {
    resolve(update.target());
    resolve(update.value());
    return report;
  }

  @Override
  public ResolutionReport visit(Stmt.If anIf) {
    resolve(anIf.condition());
    resolve(anIf.whenTrue());
    if (anIf.whenFalse() != null) resolve(anIf.whenFalse());
    return report;
  }

  @Override
  public ResolutionReport visit(Stmt.Print print) {
    resolve(print.expression());
    return report;
  }

  @Override
  public ResolutionReport visit(Stmt.Return returnStmt) {
    if (returnStmt.value() != null) resolve(returnStmt.value());
    return report;
  }

  @Override
  public ResolutionReport visit(Stmt.LoopControl loopControl) {
    return report;
  }

  @Override
  public ResolutionReport visit(Stmt.While aWhile) {
    resolve(aWhile.condition());
    resolve(aWhile.body());
    return report;
  }

  @Override
  public ResolutionReport visit(Expr.Trinary trinary) {
    resolve(trinary.head());
    resolve(trinary.left());
    resolve(trinary.right());
    return report;
  }

  @Override
  public ResolutionReport visit(Expr.Binary binary) {
    resolve(binary.left());
    resolve(binary.right());
    return report;
  }

  @Override
  public ResolutionReport visit(Expr.Logical logical) {
    resolve(logical.left());
    resolve(logical.right());
    return report;
  }

  @Override
  public ResolutionReport visit(Expr.Unary unary) {
    resolve(unary.right());
    return report;
  }

  @Override
  public ResolutionReport visit(Expr.Grouping grouping) {
    resolve(grouping.expression());
    return report;
  }

  @Override
  public ResolutionReport visit(Expr.This the) {
    resolveLocal(the.keyword());
    return report;
  }

  @Override
  public ResolutionReport visit(Expr.Literal literal) {
    return report;
  }

  @Override
  public ResolutionReport visit(Expr.Call call) {
    resolve(call.callee());
    for (Expr arg : call.arguments()) {
      resolve(arg);
    }
    return report;
  }

  @Override
  public ResolutionReport visit(Expr.Super superCall) {
    resolveLocal(superCall.keyword());
    return report;
  }

  private void resolveLocal(Token name) {
    for (int i = scopes.size() - 1; i >= 0; i--) {
      if (scopes.get(i).variables.containsKey(name.lexeme())) {
        report.locals.put(name, new Coordinates(scopes.size() - 1 - i, scopes.get(i).variables.get(name.lexeme()).id));
        return;
      }
    }
  }

  private void declare(String name, Token token) {
    if (scopes.isEmpty()) return;

    var scope = scopes.peek();
    if (scope.variables.containsKey(name)) {
      report.add(new ResolutionError(token, "Already a variable with this name in this scope."));
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
    resolveLocal(name);
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
      try(var p = scope(function)) {
        for (Token param : function.arguments()) {
          declare(param);
          define(param);
        }
        resolve(function.body());
      }
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