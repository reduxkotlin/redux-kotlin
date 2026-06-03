package org.reduxkotlin.sample.taskflow.infra.platform

/** Mints a new RFC-4122 UUID string; backing for the `IdGenerator` (OpId/CardId/etc.). */
expect fun newUuid(): String
