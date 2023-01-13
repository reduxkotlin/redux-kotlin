package org.reduxkotlin.utils

public fun isPlainObject(obj: Any): Boolean = obj !is Function<*>
