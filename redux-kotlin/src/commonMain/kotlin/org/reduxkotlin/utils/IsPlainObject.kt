package org.reduxkotlin.utils

fun isPlainObject(obj: Any) = obj !is Function<*>
