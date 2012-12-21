package com.dadrox.sbt.test.reports

class ThreadLocal[T](init: => T) extends java.lang.ThreadLocal[T] with Function0[T] {
    override def initialValue: T = init
    def apply() = get()
    def withValue[S](thunk: (T => S)): S = thunk(get)
}