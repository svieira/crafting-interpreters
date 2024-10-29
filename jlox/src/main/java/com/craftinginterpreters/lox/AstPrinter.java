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
    return parenthesize(assignment.name() + "=", assignment.value());
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
    return "($deref " + variable.name().lexeme() + ")";
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
  public String visit(Stmt.Var declaration) {
    return indent + "[var "
            + declaration.name().lexeme()
            + " "
            + declaration.initializer().accept(this)
            + "]";
  }

  @Override
  public String visit(Stmt.Block block) {
    var newDepth = indent.length() / 2 + 1;
    return indent + "[$" + newDepth
            + block
                .statements()
                .stream()
                .map(s -> s.accept(new AstPrinter(newDepth)))
                .collect(Collectors.joining(""))
            + "]\n";
  }

  @Override
  public String visit(Stmt.Unparsable errorNode) {
    return indent + "[unparsable " + errorNode + "]";
  }
}