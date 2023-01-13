package org.reduxkotlin.utils

internal fun isPlainObject(obj: Any): Boolean = obj !is Function<*>
