package com.craftinginterpreters.lox;

import java.util.List;
import java.util.function.Supplier;

import static com.craftinginterpreters.lox.TokenType.*;

class Parser {

  private static final TokenType[] EMPTY_TYPES = new TokenType[0];
  private static final TokenType[] UNAMBIGUOUS_TERM_TOKENS = { PLUS };
  private static final TokenType[] AMBIGUOUS_TERM_TOKENS = { MINUS };

  private final List<Token> tokens;
  private int current = 0;

  Parser(List<Token> tokens) {
    this.tokens = tokens;
  }

  ParseResult parse() {
    try {
      var expression = expression();
      if (!isAtEnd()) {
        return new ParseError(peek(), "Failed to finish parsing");
      }
      return expression;
    } catch (ParseError error) {
      return error;
    }
  }

  private Expr expression() {
    return discardedExpression();
  }

  private Expr discardedExpression() {
    return binaryOp(this::ternary, COMMA);
  }

  private Expr ternary() {
    if (match(QUESTION_MARK, ELVIS)) {
      // Error: Leading ternary operator
      var operator = previous();
      var _right = ternary(); // Go ahead and attempt to recover
      throw error(operator, "Ternary operator missing test condition");
    } else if (match(COLON)) {
      // Error: Leading ternary operator
      var operator = previous();
      var _right = ternary(); // Go ahead and attempt to recover
      throw error(operator, "Unexpected " + COLON + ". Are you missing a " + QUESTION_MARK + "?");
    }
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
    return binaryOp(this::factor, UNAMBIGUOUS_TERM_TOKENS, AMBIGUOUS_TERM_TOKENS);
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
      case EOF -> throw error(token, "Unexpected end of file");
      default -> {
        throw error(token, "Unable to handle token of type " + token.type());
      }
    };
  }

  /** Parse a left-associative binary operation */
  private Expr binaryOp(Supplier<Expr> target, TokenType... opTokens) {
    return binaryOp(target, opTokens, EMPTY_TYPES);
  }

  /** Parse a left-associative binary operator where some of the operators are also unary operators */
  private Expr binaryOp(Supplier<Expr> target, TokenType[] unambiguousTokens, TokenType[] ambiguousTokens) {
    if (match(unambiguousTokens)) {
      // Error: Leading binary operator
      var operator = previous();
      var _right = target.get(); // Go ahead and attempt to recover
      throw error(operator, "Binary operator missing left-hand side");
    }
    var expr = target.get();
    while (match(unambiguousTokens) || match(ambiguousTokens)) {
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
    return new ParseError(token, message);
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