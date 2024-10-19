package com.craftinginterpreters.lox;

import java.util.List;
import java.util.function.Supplier;

import static com.craftinginterpreters.lox.TokenType.*;

class Parser {
  private static class ParseError extends RuntimeException {}

  private final List<Token> tokens;
  private int current = 0;

  Parser(List<Token> tokens) {
    this.tokens = tokens;
  }

  public static void main(String... args) {
    var tokens = new Scanner("1 ? 2 ? 3 : 4 : 5").scanTokens();
    var results = new Parser(tokens).parse();
    if (results != null) {
      var repr = results.accept(new AstPrinter());
      System.out.println(repr);
    }
  }

  Expr parse() {
    try {
      return expression();
    } catch (ParseError error) {
      return null;
    }
  }

  private Expr expression() {
    return discardedExpression();
  }

  private Expr discardedExpression() {
    return binaryOp(this::ternary, COMMA);
  }

  private Expr ternary() {
    var expr = equality();
    while (match(QUESTION_MARK, ELVIS)) {
      var firstOp = previous();
      var left = ternary();
      if (firstOp.type() == ELVIS) {
        expr = new Expr.Binary(expr, firstOp, left);
      } else if (match(COLON)) {
        var secondOp = previous();
        var right = ternary();
        expr = new Expr.Trinary(expr, firstOp, left, secondOp, right);
      } else {
        throw error(previous(), "Expecting " + COLON + " following " + QUESTION_MARK);
      }
    }
    return expr;
  }

  private Expr equality() {
    return binaryOp(this::comparison, BANG_EQUAL, EQUAL_EQUAL);
  }

  private Expr comparison() {
    return binaryOp(this::term, GREATER_EQUAL, GREATER, LESS_EQUAL, LESS);
  }

  private Expr term() {
    return binaryOp(this::factor, PLUS, MINUS);
  }

  private Expr factor() {
    return binaryOp(this::unary, STAR, SLASH);
  }

  private Expr unary() {
    if (match(BANG, MINUS)) {
      Token operator = previous();
      Expr right = unary();
      return new Expr.Unary(operator, right);
    }
    return coalesce();
  }

  private Expr coalesce() {
    return binaryOp(this::primary, COALESCE);
  }

  private Expr primary() {
    var token = advance();
    return switch (token.type()) {
      case TRUE -> new Expr.Literal(true);
      case FALSE -> new Expr.Literal(false);
      case NIL -> new Expr.Literal(null);
      case NUMBER, STRING -> new Expr.Literal(token.literal());
      case LEFT_PAREN -> {
        var group = expression();
        consume(RIGHT_PAREN, "Expect ')' after expression.");
        yield new Expr.Grouping(group);
      }
      case IDENTIFIER -> new Expr.Literal(token.lexeme());
      default -> {
        throw error(token, "Unable to handle token of type " + token.type());
      }
    };
  }

  /** Parse a left-associative binary operation */
  private Expr binaryOp(Supplier<Expr> target, TokenType... opTokens) {
    var expr = target.get();
    while (match(opTokens)) {
      Token operator = previous();
      Expr right = target.get();
      expr = new Expr.Binary(expr, operator, right);
    }
    return expr;
  }

  private boolean match(TokenType... types) {
    while (check(COMMENT)) advance();
    for (TokenType type: types) {
      if (check(type)) {
        advance();
        return true;
      }
    }
    return false;
  }

  private boolean check(TokenType type) {
    if (isAtEnd()) return false;
    return peek().type() == type;
  }

  private Token advance() {
    current += 1;
    return previous();
  }

  private boolean isAtEnd() {
    return peek().type() == EOF;
  }

  private Token peek() {
    return tokens.get(current);
  }

  private Token previous() {
    return tokens.get(current - 1);
  }

  private Token consume(TokenType type, String message) {
    if (check(type)) return advance();

    throw error(peek(), message);
  }

  private ParseError error(Token token, String message) {
    Lox.error(token, message);
    return new ParseError();
  }

  private void synchronize() {
    advance();

    while (!isAtEnd()) {
      if (previous().type() == SEMICOLON) return;

      switch (peek().type()) {
        case CLASS:
        case FUN:
        case VAR:
        case FOR:
        case IF:
        case WHILE:
        case PRINT:
        case RETURN:
          return;
      }

      advance();
    }
  }
}