package com.spaceslimmer.app.ui.components



import androidx.compose.foundation.layout.PaddingValues

import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.Scaffold

import androidx.compose.material3.SnackbarHost

import androidx.compose.material3.SnackbarHostState

import androidx.compose.runtime.Composable

import androidx.compose.ui.Modifier

import androidx.compose.ui.graphics.Color



@Composable

fun SsScreen(

    modifier: Modifier = Modifier,

    topBar: @Composable () -> Unit = {},

    bottomBar: @Composable () -> Unit = {},

    snackbarHostState: SnackbarHostState? = null,

    containerColor: Color = MaterialTheme.colorScheme.background,

    content: @Composable (PaddingValues) -> Unit,

) {

    Scaffold(

        modifier = modifier,

        containerColor = containerColor,

        topBar = topBar,

        bottomBar = bottomBar,

        snackbarHost = {

            if (snackbarHostState != null) {

                SnackbarHost(hostState = snackbarHostState)

            }

        },

        content = content,

    )

}


