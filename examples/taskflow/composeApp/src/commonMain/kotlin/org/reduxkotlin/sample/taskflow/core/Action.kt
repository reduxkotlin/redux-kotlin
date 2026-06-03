package org.reduxkotlin.sample.taskflow.core

/** User card mutations only — drives the undo/redo stack. */
public interface Undoable

/** Every concrete action implements Action. */
public interface Action
