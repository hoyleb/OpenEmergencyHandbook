package org.ligi.survivalmanual.model

import android.content.SharedPreferences

/** A mistyped old preference must not prevent opening offline help. */
fun SharedPreferences.safeString(key: String, fallback: String): String = try { getString(key, fallback) ?: fallback } catch (_: ClassCastException) { fallback }
fun SharedPreferences.safeBoolean(key: String, fallback: Boolean): Boolean = try { getBoolean(key, fallback) } catch (_: ClassCastException) { fallback }
fun SharedPreferences.safeFloat(key: String, fallback: Float): Float = try { getFloat(key, fallback).takeIf { it.isFinite() } ?: fallback } catch (_: ClassCastException) { fallback }
fun SharedPreferences.safeStrings(key: String): Set<String> = (all[key] as? Set<*>)?.filterIsInstance<String>()?.toSet().orEmpty()
