package com.tiffles.travelingtavern.data.webview

import com.tiffles.travelingtavern.domain.model.PageError
import com.tiffles.travelingtavern.domain.model.PageState
import org.junit.Assert.assertEquals
import org.junit.Test

class PageStateTrackerTest {

    private val tracker = PageStateTracker()

    @Test
    fun `starts in loading`() {
        assertEquals(PageState.Loading, tracker.state.value)
    }

    @Test
    fun `a finished load without errors is ready`() {
        tracker.onPageStarted()
        tracker.onPageFinished()

        assertEquals(PageState.Ready, tracker.state.value)
    }

    @Test
    fun `an error survives the page finished callback that follows it`() {
        tracker.onPageStarted()
        tracker.onMainFrameError(PageError.Unreachable)
        tracker.onPageFinished()

        assertEquals(PageState.Error(PageError.Unreachable), tracker.state.value)
    }

    @Test
    fun `the first error of a load wins`() {
        tracker.onPageStarted()
        tracker.onMainFrameError(PageError.AuthFailed)
        tracker.onMainFrameError(PageError.HttpError(code = 401))

        assertEquals(PageState.Error(PageError.AuthFailed), tracker.state.value)
    }

    @Test
    fun `a new load clears the previous error`() {
        tracker.onPageStarted()
        tracker.onMainFrameError(PageError.HttpError(code = 502))

        tracker.onPageStarted()
        assertEquals(PageState.Loading, tracker.state.value)

        tracker.onPageFinished()
        assertEquals(PageState.Ready, tracker.state.value)
    }
}
