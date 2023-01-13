package org.reduxkotlin

public sealed interface ActionTypes {
  /**
   * Default start action sent when store is created.
   */
  public object INIT : ActionTypes

  /**
   * Action sent when reducer is replaced.
   */
  public object REPLACE : ActionTypes
}
