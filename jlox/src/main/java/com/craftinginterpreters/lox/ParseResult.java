package com.craftinginterpreters.lox;

/** The possible productions of an attempt to parse some LOX code:
 * <ul>
 *    <li>A valid expression {@link Expr}</li>
 *    <li>A parse error {@link ParseError}</li>
 * </ul>  */
public sealed interface ParseResult permits Expr, ParseError {
}
