package com.savoo.scclient.data.local

import com.savoo.scclient.compat.Context
import com.savoo.scclient.compat.SharedPreferences

fun createSecurePrefs(context: Context, fileName: String): SharedPreferences =
    context.getSharedPreferences(fileName, Context.MODE_PRIVATE)

fun openSecurePrefsStrict(context: Context, fileName: String): SharedPreferences =
    createSecurePrefs(context, fileName)
