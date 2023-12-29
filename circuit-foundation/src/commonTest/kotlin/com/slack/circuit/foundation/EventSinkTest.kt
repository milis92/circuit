// Copyright (C) 2023 Slack Technologies, LLC
// SPDX-License-Identifier: Apache-2.0
package com.slack.circuit.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.TurbineTestContext
import app.cash.turbine.test
import com.slack.circuit.foundation.EventTestCircuit.TestEvent
import com.slack.circuit.foundation.EventTestCircuit.TestState
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.presenter.presenterOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlinx.coroutines.test.runTest

private const val ONE = 1
private const val TWO = 2
private const val THREE = 3

/** Expected test */
expect class EventSinkTest() : EventSinkTestShared

/** Shared test interface */
internal interface EventSinkTestShared {

  @Test fun testTrailing()

  @Test fun testLocalFun()

  @Test fun testVal()

  @Test fun testRemember()

  @Test fun testMutateTrailing()

  @Test fun testMutateLocalFun()

  @Test fun testMutateVal()

  @Test fun testMutateRemember()
}

class EventSinkTestSharedImpl : EventSinkTestShared {

  @Test
  override fun testTrailing() = testEventSinkEquals {
    var count by remember { mutableIntStateOf(0) }
    // Stable, nothing to change the lambda
    TestState(count) { event ->
      count =
        when (event) {
          is TestEvent.Inverse -> {
            -count
          }
          is TestEvent.Count -> {
            event.num
          }
        }
    }
  }

  @Test
  override fun testLocalFun() = testEventSinkEquals {
    var count by remember { mutableIntStateOf(0) }
    // Stable, nothing to change the function
    fun eventSink(event: TestEvent) {
      count =
        when (event) {
          is TestEvent.Inverse -> {
            -count
          }
          is TestEvent.Count -> {
            event.num
          }
        }
    }
    TestState(count, eventSink = ::eventSink)
  }

  @Test
  override fun testVal() = testEventSinkEquals {
    var count by remember { mutableIntStateOf(0) }
    // Stable, nothing to change the lambda
    val eventSink = { event: TestEvent ->
      count =
        when (event) {
          is TestEvent.Inverse -> {
            -count
          }
          is TestEvent.Count -> {
            event.num
          }
        }
    }
    TestState(count, eventSink = eventSink)
  }

  @Test
  override fun testRemember() = testEventSinkEquals {
    var count by remember { mutableIntStateOf(0) }
    // Stable, remember doesn't recalculate
    val eventSink = remember {
      { event: TestEvent ->
        count =
          when (event) {
            TestEvent.Inverse -> {
              -count
            }
            is TestEvent.Count -> {
              event.num
            }
          }
      }
    }
    TestState(count, eventSink = eventSink)
  }

  @Test
  override fun testMutateTrailing() = testEventSinkNotEquals {
    var key by remember { mutableIntStateOf(0) }
    var count by remember(key) { mutableIntStateOf(0) }
    // Key change creates a new count instance resulting in a new lambda
    TestState(count) { event ->
      when (event) {
        TestEvent.Inverse -> {
          count = -key
        }
        is TestEvent.Count -> {
          key = event.num
          count = event.num
        }
      }
    }
  }

  @Test
  override fun testMutateLocalFun() = testEventSinkEquals {
    var key by remember { mutableIntStateOf(0) }
    var count by remember(key) { mutableIntStateOf(key) }

    // todo This works...?
    fun eventSink(event: TestEvent) {
      when (event) {
        TestEvent.Inverse -> {
          count = -key
        }
        is TestEvent.Count -> {
          key = event.num
          count = event.num // Assigns to the new count?
        }
      }
    }
    TestState(count, eventSink = ::eventSink)
  }

  @Test
  override fun testMutateVal() = testEventSinkNotEquals {
    var key by remember { mutableIntStateOf(0) }
    var count by remember(key) { mutableIntStateOf(0) }
    // Key change creates a new count instance resulting in a new lambda
    val eventSink = { event: TestEvent ->
      when (event) {
        TestEvent.Inverse -> {
          count = -key
        }
        is TestEvent.Count -> {
          key = event.num
          count = event.num // Assigns to the old count
        }
      }
    }
    TestState(count, eventSink = eventSink)
  }

  @Test
  override fun testMutateRemember() = testEventSinkNotEquals {
    var key by remember { mutableIntStateOf(0) }
    var count by remember(key) { mutableIntStateOf(0) }
    // Key change creates a new lambda
    val eventSink =
      remember(key) {
        { event: TestEvent ->
          when (event) {
            TestEvent.Inverse -> {
              count = -key
            }
            is TestEvent.Count -> {
              key = event.num
              count = event.num // Assigns to the old count
            }
          }
        }
      }
    TestState(count, eventSink = eventSink)
  }

  private class PresentationSinkState : (TestEvent) -> Unit {

    var key by mutableIntStateOf(0)

    // todo simulate the remember(key) in the others
    var count by mutableIntStateOf(0)

    override operator fun invoke(event: TestEvent) {
      when (event) {
        TestEvent.Inverse -> {
          count = -key
        }
        is TestEvent.Count -> {
          key = event.num
          count = event.num
        }
      }
    }
  }

  @Test
  fun testMutateObject() = testEventSinkEquals {
    val sinkState = remember { PresentationSinkState() }
    TestState(sinkState.count, eventSink = sinkState)
  }

  private fun testEventSinkEquals(
    vararg counts: Int = intArrayOf(ONE, TWO, THREE),
    presenter: @Composable () -> TestState
  ) =
    runEventSinkTest(presenter) {
      // Setup
      var state = awaitItem()
      val expectedEventSink = state.eventSink
      // One
      state = awaitState(state, TestEvent.Count(counts[0]))
      assertEquals(TestState(counts[0], expectedEventSink), state)
      // Inverse
      state = awaitState(state, TestEvent.Inverse)
      assertEquals(TestState(-counts[0], expectedEventSink), state)
      // Two
      state = awaitState(state, TestEvent.Count(counts[1]))
      assertEquals(TestState(counts[1], expectedEventSink), state)
      // Inverse
      state = awaitState(state, TestEvent.Inverse)
      assertEquals(TestState(-counts[1], expectedEventSink), state)
      // Three
      state = awaitState(state, TestEvent.Count(counts[2]))
      assertEquals(TestState(counts[2], expectedEventSink), state)
      // Inverse
      state = awaitState(state, TestEvent.Inverse)
      assertEquals(TestState(-counts[2], expectedEventSink), state)
    }

  private fun testEventSinkNotEquals(
    vararg counts: Int = intArrayOf(ONE, TWO, THREE),
    presenter: @Composable () -> TestState
  ) =
    runEventSinkTest(presenter) {
      // Setup
      var state = awaitItem()
      val expectedEventSink = state.eventSink
      // One
      state = awaitState(state, TestEvent.Count(counts[0]))
      assertEquals(0, state.count)
      assertNotEquals(expectedEventSink, state.eventSink)
      // Inverse
      state = awaitState(state, TestEvent.Inverse)
      assertEquals(-counts[0], state.count)
      assertNotEquals(expectedEventSink, state.eventSink)
      // Two
      state = awaitState(state, TestEvent.Count(counts[1]))
      assertEquals(0, state.count)
      assertNotEquals(expectedEventSink, state.eventSink)
      // Inverse
      state = awaitState(state, TestEvent.Inverse)
      assertEquals(-counts[1], state.count)
      assertNotEquals(expectedEventSink, state.eventSink)
      // Three
      state = awaitState(state, TestEvent.Count(counts[2]))
      assertEquals(0, state.count)
      assertNotEquals(expectedEventSink, state.eventSink)
      // Inverse
      state = awaitState(state, TestEvent.Inverse)
      assertEquals(-counts[2], state.count)
      assertNotEquals(expectedEventSink, state.eventSink)
    }

  private fun runEventSinkTest(
    presenter: @Composable () -> TestState,
    validate: suspend TurbineTestContext<TestState>.() -> Unit,
  ) {
    runTest {
      moleculeFlow(RecompositionMode.Immediate) { presenterOf(presenter).present() }
        .test(validate = validate)
    }
  }
}

private suspend fun TurbineTestContext<TestState>.awaitState(
  state: TestState,
  event: TestEvent,
): TestState {
  state.eventSink(event)
  return awaitItem()
}

private object EventTestCircuit {

  data class TestState(
    val count: Int,
    val eventSink: (TestEvent) -> Unit,
  ) : CircuitUiState

  sealed interface TestEvent : CircuitUiEvent {
    data object Inverse : TestEvent

    data class Count(val num: Int) : TestEvent
  }
}
