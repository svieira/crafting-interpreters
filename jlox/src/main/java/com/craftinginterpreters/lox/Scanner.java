package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.craftinginterpreters.lox.TokenType.*;
import static java.util.Map.entry;

class Scanner {
  private static final Map<String, TokenType> keywords = Map.ofEntries(
          entry("and", AND),
          entry("class", CLASS),
          entry("else", ELSE),
          entry("false", FALSE),
          entry("for", FOR),
          entry("fun", FUN),
          entry("if", IF),
          entry("nil", NIL),
          entry("or", OR),
          entry("print", PRINT),
          entry("return", RETURN),
          entry("super", SUPER),
          entry("this", THIS),
          entry("true", TRUE),
          entry("var", VAR),
          entry("while", WHILE),
          entry("break", BREAK),
          entry("continue", CONTINUE)
  );

  private final String source;
  private final TokenList tokens = new TokenList();
  private LexError error;
  private int start = 0;
  private int current = 0;
  private int line = 1;
  private int column = 0;

  Scanner(String source) {
    this.source = source;
  }

  public sealed interface ScanResults permits TokenList, LexError {}
  public static final class TokenList extends ArrayList<Token> implements ScanResults {}
  public static final class LexError extends RuntimeException implements ScanResults {
    private final int line;
    private final int column;

    LexError(String message, int line, int column) {
      super(message);
      this.line = line;
      this.column = column;
    }

    public int getLine() {
      return line;
    }

    public int getColumn() {
      return column;
    }
  }

  ScanResults scanTokens() {
    while (!isAtEnd()) {
      // We are at the beginning of the next lexeme.
      start = current;
      scanToken();
    }

    if (error != null) {
      return error;
    }

    tokens.add(new Token(EOF, "", null, line, column));
    return tokens;
  }

  private boolean isAtEnd() {
    return current >= source.length();
  }

  private void scanToken() {
    char c = advance();
    switch (c) {
      case '(' -> addToken(LEFT_PAREN);
      case ')' -> addToken(RIGHT_PAREN);
      case '{' -> addToken(LEFT_BRACE);
      case '}' -> addToken(RIGHT_BRACE);
      case ',' -> addToken(COMMA);
      case '.' -> addToken(DOT);
      case '-' -> addToken(MINUS);
      case '+' -> addToken(PLUS);
      case ';' -> addToken(SEMICOLON);
      case '*' -> addToken(STAR);
      case '!' -> addToken(match('=') ? BANG_EQUAL : BANG);
      case '=' -> addToken(match('=') ? EQUAL_EQUAL : EQUAL);
      case '<' -> addToken(match('=') ? LESS_EQUAL : LESS);
      case '>' -> addToken(match('=') ? GREATER_EQUAL : GREATER);
      case '?' -> addToken(
              match(':')
                      ? ELVIS
                      : match('?') ? COALESCE : QUESTION_MARK);
      case ':' -> addToken(COLON);
      case '/' -> {
        if (match('/')) {
          // A line comment goes until the end of the line.
          while (peek() != '\n' && !isAtEnd()) advance();
          // addToken(COMMENT);
        } else if (match('*')) {
          var blockCommentBeginningLineNumber = line;
          var blockCommentBeginningColumnNumber = column;
          var blockCommentDepth = 1;
          while (blockCommentDepth > 0) {
            if (isAtEnd()) {
              error = new LexError("Unclosed block comment detected", blockCommentBeginningLineNumber, blockCommentBeginningColumnNumber);
              break;
            }
            switch (advance()) {
              case '*' -> {
                if (match('/')) {
                  blockCommentDepth -= 1;
                }
              }
              case '/' -> {
                if (match('*')) {
                  blockCommentDepth += 1;
                }
              }
            }
          }
          // addToken(COMMENT);
        } else {
          addToken(SLASH);
        }
      }
      case ' ', '\r', '\t' -> { /* deliberately ignored - this is an AST not a CST. */ }
      case '\n' -> onNewLine();
      case '"' -> string();
      default -> {
        if (isDigit(c)) {
          number();
        } else if (isAlpha(c)) {
          identifier();
        } else {
          addToken(INVALID, c);
          // TODO: Continue lexing here if we've got a surrogate to try to get the real character the user typed.
          error = new LexError("Unexpected character: '" + c + "' (" + Character.getName(c) + ")", line, column);
        }
      }
    }
  }

  private int onNewLine() {
    column = 0;
    return line++;
  }

  private void identifier() {
    while (isAlphaNumeric(peek())) advance();
    String text = source.substring(start, current);
    TokenType type = keywords.getOrDefault(text, IDENTIFIER);
    addToken(type);
  }

  private void number() {
    while (isDigit(peek())) advance();

    // Look for a fractional part.
    if (peek() == '.' && isDigit(peekNext())) {
      // Consume the "."
      advance();

      while (isDigit(peek())) advance();
    }

    addToken(NUMBER, Double.parseDouble(source.substring(start, current)));
  }

  private void string() {
    var stringStartLine = line;
    var stringStartColumn = column;
    while (peek() != '"' && !isAtEnd()) {
      if (peek() == '\n') onNewLine();
      advance();
    }

    if (isAtEnd()) {
      error = new LexError("Unterminated string.", stringStartLine, stringStartColumn);
      return;
    }

    // The closing ".
    advance();

    // Trim the surrounding quotes.
    String value = source.substring(start + 1, current - 1);
    addToken(STRING, value);
  }

  /**
   * Advance the character pointer if the current value is expected.
   * @param expected the character that is expected.
   * @return the character matched and we have moved the scanner head to the next character
   */
  private boolean match(char expected) {
    if (isAtEnd()) return false;
    if (source.charAt(current) != expected) return false;

    current++;
    return true;
  }

  private char advance() {
    column += 1;
    return source.charAt(current++);
  }

  private char peek() {
    if (isAtEnd()) return '\0';
    return source.charAt(current);
  }

  private char peekNext() {
    if (current + 1 >= source.length()) return '\0';
    return source.charAt(current + 1);
  }

  private boolean isAlpha(char c) {
    return (c >= 'a' && c <= 'z') ||
            (c >= 'A' && c <= 'Z') ||
            c == '_';
  }

  private boolean isAlphaNumeric(char c) {
    return isAlpha(c) || isDigit(c);
  }

  private boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  }

  private void addToken(TokenType type) {
    addToken(type, null);
  }

  private void addToken(TokenType type, Object literal) {
    String text = source.substring(start, current);
    tokens.add(new Token(type, text, literal, line, column));
  }
}