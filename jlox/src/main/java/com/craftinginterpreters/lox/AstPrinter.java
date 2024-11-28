package com.craftinginterpreters.lox;

import java.util.stream.Collectors;

class AstPrinter implements Expr.Visitor<String>, Stmt.Visitor<String> {
  private final String indent;

  AstPrinter() {
    this(0);
  }

  private AstPrinter(int depth) {
    this.indent = new StringBuilder(depth * 2).append('\n').repeat(' ', depth * 2).toString();
  }

  @Override
  public String visit(Expr.Binary binary) {
    return parenthesize(binary.operator().lexeme(), binary.left(), binary.right());
  }

  @Override
  public String visit(Expr.Logical logical) {
    return parenthesize(logical.operator().lexeme(), logical.left(), logical.right());
  }

  @Override
  public String visit(Expr.Grouping grouping) {
    return parenthesize("group", grouping.expression());
  }

  @Override
  public String visit(Expr.Literal literal) {
    if (literal.value() == null) return "nil";
    return literal.value().toString();
  }

  @Override
  public String visit(Expr.Unary unary) {
    return parenthesize(unary.operator().lexeme(), unary.right());
  }

  @Override
  public String visit(Expr.Assignment assignment) {
    return parenthesize(assignment.name().lexeme() + "=", assignment.value());
  }

  @Override
  public String visit(Expr.Trinary trinary) {
    return "("
            + trinary.firstOp().lexeme() + " " + trinary.head().accept(this) + " "
            + parenthesize(trinary.secondOp().lexeme(), trinary.left(), trinary.right())
            + ")";
  }

  @Override
  public String visit(Expr.Variable variable) {
    return variable.name().lexeme();
  }

  @Override
  public String visit(Expr.Call call) {
    return parenthesize(call.callee().accept(this), call.arguments().toArray(new Expr[0]));
  }

  @Override
  public String visit(Expr.Function f) {
    return "(λ" + f.name().lexeme() + " [" + f.arguments().stream().map(Token::lexeme).collect(Collectors.joining(" ")) + "]\n"
            + f.body().stream().map(s -> s.accept(new AstPrinter(indent()))).collect(Collectors.joining("\n"))
            + ")";
  }

  @Override
  public String visit(Expr.Unparseable unparsable) {
    return "($unparsable " + unparsable + ")";
  }

  private String parenthesize(String name, Expr... exprs) {
    StringBuilder builder = new StringBuilder();

    builder.append("(").append(name);
    for (Expr expr : exprs) {
      builder.append(" ");
      builder.append(expr.accept(this));
    }
    builder.append(")");

    return builder.toString();
  }

  @Override
  public String visit(Stmt.Expression expression) {
    return indent + "[void " + expression.expression().accept(this) + "]";
  }

  @Override
  public String visit(Stmt.Print print) {
    return indent + "[print " + print.expression().accept(this) + "]";
  }

  @Override
  public String visit(Stmt.Function function) {
    return indent + "[defun " + function.name().lexeme()
            + " [" + function.params().stream().map(Token::lexeme).collect(Collectors.joining(", "))
            + "]"
            + function.body().stream().map(s -> s.accept(new AstPrinter(indent()))).collect(Collectors.joining("\n"))
            + "]";
  }

  @Override
  public String visit(Stmt.Return returnStmt) {
    return indent + "[return " + returnStmt.value().accept(new AstPrinter(indent())) + "]";
  }

  @Override
  public String visit(Stmt.Var declaration) {
    var variableName = indent + "[var " + declaration.name().lexeme();
    if (declaration.initializer() == null) return variableName + "]";
    return variableName
            + " "
            + declaration.initializer().accept(this)
            + "]";
  }

  @Override
  public String visit(Stmt.Block block) {
    var newDepth = indent();
    return visit(block, "$" + newDepth);
  }

  private int indent() {
    return indent.length() / 2 + 1;
  }

  private String visit(Stmt.Block block, String blockName) {
    var newDepth = indent();
    return indent + "[" + blockName
            + block
            .statements()
            .stream()
            .map(s -> s.accept(new AstPrinter(newDepth)))
            .collect(Collectors.joining(""))
            + "]";
  }

  @Override
  public String visit(Stmt.If anIf) {
    return indent + "[if "
            + parenthesize(anIf.condition().accept(this))
            + anIf.whenTrue().accept(new AstPrinter(indent()))
            + anIf.whenFalse().accept(new AstPrinter(indent())) + "]";
  }

  @Override
  public String visit(Stmt.While aWhile) {
    return indent + "[while "
            + parenthesize(aWhile.condition().accept(this))
            + aWhile.body().accept(new AstPrinter(indent())) + "]";
  }

  @Override
  public String visit(Stmt.LoopControl control) {
    return indent + "[" + control.type().toString().toLowerCase() + "]";
  }

  @Override
  public String visit(Stmt.Unparsable errorNode) {
    return indent + "[unparsable " + errorNode + "]";
  }
}