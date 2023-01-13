package org.reduxkotlin

internal sealed interface ActionTypes {
  /**
   * Default start action sent when store is created.
   */
  object INIT : ActionTypes

  /**
   * Action sent when reducer is replaced.
   */
  object REPLACE : ActionTypes
}
