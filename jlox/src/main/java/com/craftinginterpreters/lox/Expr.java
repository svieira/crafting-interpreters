package com.craftinginterpreters.lox;

import java.util.List;

/**
 * <p>The AST for the expression portion of the Lox grammar:</p>
 * <pre><code>
 * expression     → assignment ;
 * assignment     → ( call "." )? IDENTIFIER "=" assignment
 *                | logic_or ;
 * logic_or       → logic_and ( "or" logic_and )* ;
 * logic_and      → discarded ( "and" discarded )* ;
 * discarded      → ternary ( "," ternary )* ;
 * ternary        → equality (( "?" ternary ":" ternary ) | "?:" ternary )* ;
 * equality       → comparison ( ( "!=" | "==" ) comparison )* ;
 * comparison     → term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
 * term           → factor ( ( "-" | "+" ) factor )* ;
 * factor         → unary ( ( "/" | "*" ) unary )* ;
 * unary          → ( "!" | "-" ) unary
 *                | coalesce ;
 * coalesce       → call ( "??" call )* ;
 * call           → primary ( "(" arguments? ")" | "." IDENTIFIER )* ;
 * arguments      → expression ( "," expression )* ;
 * primary        → NUMBER | STRING | "true" | "false" | "nil"
 *                | "(" expression ")"
 *                | "fun" IDENTIFIER? "(" arguments ")" "{" declaration* "}"
 *                | IDENTIFIER ;
 * </code></pre>

 * {@link Stmt see also <code>Stmt</code>}
 */
non-sealed interface Expr extends ParseResult {
  interface Visitor<R> {
    R visit(Trinary trinary);
    R visit(Binary binary);
    R visit(Logical logical);
    R visit(Unary unary);
    R visit(Grouping grouping);
    R visit(Literal literal);
    R visit(Variable variable);
    R visit(Assignment assignment);
    R visit(Call call);
    R visit(Function call);
    R visit(Select select);
    R visit(Update update);
    R visit(This the);
    default R visit(Unparseable unparseable) {
      throw new UnsupportedOperationException("Did not expect to have to handle an unparsable expression");
    }
  }

  <R> R accept(Visitor<R> visitor);

  record Trinary(Expr head, Token firstOp, Expr left, Token secondOp, Expr right) implements Expr {
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visit(this);
    }
  }
  record Binary(Expr left, Token operator, Expr right) implements Expr {
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visit(this);
    }
  }
  record Logical(Expr left, Token operator, Expr right) implements Expr {
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visit(this);
    }
  }
  record Grouping(Expr expression) implements Expr {
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visit(this);
    }
  }
  record Literal(Object value) implements Expr {
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visit(this);
    }
  }
  record Unary(Token operator, Expr right) implements Expr {
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visit(this);
    }
  }
  record Variable(Token name) implements Expr {
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visit(this);
    }
  }
  record Assignment(Token name, Expr value) implements Expr {
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visit(this);
    }
  }
  record Function(Token keyword, Token name, List<Token> arguments, List<Stmt> body, boolean isAnonymous) implements Expr {
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visit(this);
    }
  }
  record Call(Expr callee, Token paren, List<Expr> arguments) implements Expr {
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visit(this);
    }
  }
  record Select(Expr target, Token field) implements Expr {
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visit(this);
    }
  }
  record Update(Expr target, Token field, Expr value) implements Expr {
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visit(this);
    }
  }
  record This(Token keyword) implements Expr {
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visit(this);
    }
  }
  record Unparseable(Token start, Token end) implements Expr {
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visit(this);
    }
  }
}