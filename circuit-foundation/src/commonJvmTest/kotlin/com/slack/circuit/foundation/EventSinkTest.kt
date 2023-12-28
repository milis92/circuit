// Copyright (C) 2023 Slack Technologies, LLC
// SPDX-License-Identifier: Apache-2.0
package com.slack.circuit.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import com.slack.circuit.foundation.EventTestCircuit.TestEvent
import com.slack.circuit.foundation.EventTestCircuit.TestState
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.presenter.presenterOf
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val UNSET = Int.MIN_VALUE
private const val ONE = 1
private const val TWO = 2
private const val THREE = 3

@RunWith(ComposeUiTestRunner::class)
class EventSinkTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun testTrailing() = testEventSinkEquals {
    var count by remember { mutableIntStateOf(0) }
    TestState(count) { event ->
      count =
        when (event) {
          TestEvent.One -> ONE
          TestEvent.Two -> TWO
          TestEvent.Three -> THREE
        }
    }
  }

  @Test
  fun testLocalFun() = testEventSinkEquals {
    var count by remember { mutableIntStateOf(0) }
    fun eventSink(event: TestEvent) {
      count =
        when (event) {
          TestEvent.One -> ONE
          TestEvent.Two -> TWO
          TestEvent.Three -> THREE
        }
    }
    TestState(count, eventSink = ::eventSink)
  }

  @Test
  fun testVal() = testEventSinkEquals {
    var count by remember { mutableIntStateOf(0) }
    val eventSink = { event: TestEvent ->
      count =
        when (event) {
          TestEvent.One -> ONE
          TestEvent.Two -> TWO
          TestEvent.Three -> THREE
        }
    }
    TestState(count, eventSink = eventSink)
  }

  @Test
  fun testRemember() = testEventSinkEquals {
    var count by remember { mutableIntStateOf(0) }
    val eventSink = remember {
      { event: TestEvent ->
        count =
          when (event) {
            TestEvent.One -> ONE
            TestEvent.Two -> TWO
            TestEvent.Three -> THREE
          }
      }
    }
    TestState(count, eventSink = eventSink)
  }

  @Test
  fun testMutateTrailing() = testEventSinkNotEquals {
    var key by remember { mutableIntStateOf(0) }
    var count by remember(key) { mutableIntStateOf(key) }
    // Fails as the new count state instance results in a new lambda
    TestState(count) { event ->
      val num =
        when (event) {
          TestEvent.One -> ONE
          TestEvent.Two -> TWO
          TestEvent.Three -> THREE
        }
      key = num
      count = num
    }
  }

  @Test
  fun testMutateLocalFun() = testEventSinkEquals {
    var key by remember { mutableIntStateOf(0) }
    var count by remember(key) { mutableIntStateOf(key) }

    // This works...
    fun eventSink(event: TestEvent) {
      val num =
        when (event) {
          TestEvent.One -> ONE
          TestEvent.Two -> TWO
          TestEvent.Three -> THREE
        }
      key = num
      count = num
    }
    TestState(count, eventSink = ::eventSink)
  }

  @Test
  fun testMutateVal() = testEventSinkNotEquals {
    var key by remember { mutableIntStateOf(0) }
    var count by remember(key) { mutableIntStateOf(key) }
    // Fails as the new count state instance results in a new lambda
    val eventSink = { event: TestEvent ->
      val num =
        when (event) {
          TestEvent.One -> ONE
          TestEvent.Two -> TWO
          TestEvent.Three -> THREE
        }
      key = num
      count = num
    }
    TestState(count, eventSink = eventSink)
  }

  @Test
  fun testMutateRemember() = testEventSinkNotEquals {
    var key by remember { mutableIntStateOf(0) }
    var count by remember(key) { mutableIntStateOf(key) }
    // Fails as the new key value results in a new lambda
    val eventSink =
      remember(key) {
        { event: TestEvent ->
          val num =
            when (event) {
              TestEvent.One -> ONE
              TestEvent.Two -> TWO
              TestEvent.Three -> THREE
            }
          key = num
          count = num
        }
      }
    TestState(count, eventSink = eventSink)
  }

  private class PresentationSinkState : (TestEvent) -> Unit {

    var key by mutableIntStateOf(0)
    var count = mutableIntStateOf(key)

    override operator fun invoke(event: TestEvent) {
      val num =
        when (event) {
          TestEvent.One -> ONE
          TestEvent.Two -> TWO
          TestEvent.Three -> THREE
        }
      key = num
      // TODO Compose doesn't know this changed yet
      count = mutableIntStateOf(key)
    }
  }

  @Test
  fun testMutateObject() = testEventSinkEquals {
    val sinkState = remember { PresentationSinkState() }
    TestState(sinkState.count.value, eventSink = sinkState)
  }

  private fun testEventSinkEquals(presenter: @Composable () -> TestState) = runTest {
    composeTestRule.run {
      var state = TestState(UNSET) { throw IllegalStateException("State not set") }
      setContent { state = presenterOf(presenter).present() }
      // Setup
      awaitIdle()
      val expectedEventSink = state.eventSink
      // One
      state.eventSink(TestEvent.One)
      awaitIdle()
      assertEquals(TestState(ONE, expectedEventSink), state)
      // Two
      state.eventSink(TestEvent.Two)
      awaitIdle()
      assertEquals(TestState(TWO, expectedEventSink), state)
      // Three
      state.eventSink(TestEvent.Three)
      awaitIdle()
      assertEquals(TestState(THREE, expectedEventSink), state)
    }
  }

  private fun testEventSinkNotEquals(presenter: @Composable () -> TestState) = runTest {
    composeTestRule.run {
      var state = TestState(UNSET) { throw IllegalStateException("State not set") }
      setContent { state = presenterOf(presenter).present() }
      // Setup
      awaitIdle()
      val expectedEventSink = state.eventSink
      // One
      state.eventSink(TestEvent.One)
      awaitIdle()
      assertEquals(ONE, state.count)
      assertNotEquals(expectedEventSink, state.eventSink)
      // Two
      state.eventSink(TestEvent.Two)
      awaitIdle()
      assertEquals(TWO, state.count)
      assertNotEquals(expectedEventSink, state.eventSink)
      // Three
      state.eventSink(TestEvent.Three)
      awaitIdle()
      assertEquals(THREE, state.count)
      assertNotEquals(expectedEventSink, state.eventSink)
    }
  }
}

private object EventTestCircuit {

  data class TestState(
    val count: Int,
    val eventSink: (TestEvent) -> Unit,
  ) : CircuitUiState

  sealed interface TestEvent : CircuitUiEvent {
    data object One : TestEvent

    data object Two : TestEvent

    data object Three : TestEvent
  }
}
