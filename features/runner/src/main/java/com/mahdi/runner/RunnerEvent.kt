package com.mahdi.runner

import com.mahdi.events.Event

sealed interface RunnerEvent : Event {

    data class RunnerRun(val runner: Runner) : RunnerEvent
}
