package dev.agentsforcursor.util

import dev.agentsforcursor.data.net.ApiException
import java.io.IOException

fun Throwable.userMessage(): String = when {
    this is ApiException -> message
    this is IOException -> "No connection to the Cursor API. Check your network and try again."
    else -> message?.takeIf { it.isNotBlank() } ?: "Something went wrong."
}
