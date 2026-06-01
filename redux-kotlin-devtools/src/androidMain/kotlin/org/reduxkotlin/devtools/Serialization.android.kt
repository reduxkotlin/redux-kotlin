package org.reduxkotlin.devtools

// Android: reflection via kotlin-reflect is unreliable after R8/ProGuard shrinking,
// so fall back to the universal toString tier.
internal actual fun platformDefaultSerializer(): ValueSerializer = ToStringValueSerializer
