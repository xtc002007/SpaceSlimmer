package com.spaceslimmer.videohw



import androidx.media3.common.util.UnstableApi

import androidx.media3.transformer.Transformer

import java.util.concurrent.ConcurrentHashMap

import java.util.concurrent.atomic.AtomicLong



@UnstableApi

object VideoTranscodeRegistry {

    private data class Session(val token: Long, val cancel: () -> Unit)



    private val sessions = ConcurrentHashMap<String, Session>()

    private val nextToken = AtomicLong(0)



    fun register(jobId: String, transformer: Transformer): Long {

        val token = nextToken.incrementAndGet()

        sessions[jobId] = Session(token) { transformer.cancel() }

        return token

    }



    fun register(jobId: String, cancel: () -> Unit): Long {

        val token = nextToken.incrementAndGet()

        sessions[jobId] = Session(token, cancel)

        return token

    }



    fun unregister(jobId: String, token: Long) {

        sessions.computeIfPresent(jobId) { _, session ->

            if (session.token == token) null else session

        }

    }



    fun cancel(jobId: String) {

        sessions.remove(jobId)?.cancel?.invoke()

    }

}


