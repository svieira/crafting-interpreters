package com.craftinginterpreters.lox;

import java.util.EnumSet;

public class EnumSetQueue<T extends Enum<T>> {
  private final EnumSet<T> head;
  private final EnumSetQueue<T> tail;
  public static <T extends Enum<T>> EnumSetQueue<T> empty(Class<T> enumType) {
    return new EnumSetQueue<>(EnumSet.noneOf(enumType));
  }
  @SafeVarargs
  public static <T extends Enum<T>> EnumSetQueue<T> push(EnumSetQueue<T> tail, T first, T... additional) {
    if (tail == null) {
      return push(first, additional);
    }
    return new EnumSetQueue<>(EnumSet.of(first, additional), tail);
  }

  @SafeVarargs
  public static <T extends Enum<T>> EnumSetQueue<T> push(T first, T... additional) {
    return new EnumSetQueue<>(EnumSet.of(first, additional));
  }

  public static <T extends Enum<T>> EnumSetQueue<T> pop(EnumSetQueue<T> queue) {
    return queue == null ? queue : queue.tail;
  }

  private EnumSetQueue(EnumSet<T> head) {
    this.head = head;
    this.tail = null;
  }

  private EnumSetQueue(EnumSet<T> head, EnumSetQueue<T> tail) {
    this.head = head;
    this.tail = tail;
  }

  public boolean contains(T context) {
    return head.contains(context) || tail != null && tail.contains(context);
  }
}
