package com.craftinginterpreters.lox;

class AstPrinter implements Expr.Visitor<String> {
  String print(Expr expr) {
    return expr.accept(this);
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
}