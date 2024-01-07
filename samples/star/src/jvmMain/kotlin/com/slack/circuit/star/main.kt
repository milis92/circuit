// Copyright (C) 2022 Slack Technologies, LLC
// SPDX-License-Identifier: Apache-2.0
package com.slack.circuit.star

import androidx.compose.material.MaterialTheme
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.SingletonImageLoader
import com.slack.circuit.backstack.rememberSaveableBackStack
import com.slack.circuit.foundation.CircuitCompositionLocals
import com.slack.circuit.foundation.NavigableCircuitContent
import com.slack.circuit.foundation.rememberCircuitNavigator
import com.slack.circuit.overlay.ContentWithOverlays
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.star.di.AppComponent
import com.slack.circuit.star.home.HomeScreen
import kotlinx.collections.immutable.persistentListOf

fun main() {
  val component = AppComponent.create()
  SingletonImageLoader.setSafe { component.imageLoader }
  application {
    val initialBackStack = persistentListOf<Screen>(HomeScreen)
    val backStack = rememberSaveableBackStack { initialBackStack.forEach(::push) }
    val navigator = rememberCircuitNavigator(backStack, ::exitApplication)
    val windowState = rememberWindowState(width = 1200.dp, height = 800.dp)
    Window(
      title = "STAR",
      state = windowState,
      onCloseRequest = ::exitApplication,
      // In lieu of a global shortcut handler, we best-effort with this
      // https://github.com/JetBrains/compose-multiplatform/issues/914
      onKeyEvent = {
        when (it.key) {
          Key.Escape -> {
            if (backStack.size > 1) {
              navigator.pop()
              true
            } else {
              false
            }
          }
          else -> false
        }
      }
    ) {
      MaterialTheme {
        CircuitCompositionLocals(component.circuit) {
          ContentWithOverlays {
            NavigableCircuitContent(
              navigator = navigator,
              backstack = backStack,
            )
          }
        }
      }
    }
  }
}
