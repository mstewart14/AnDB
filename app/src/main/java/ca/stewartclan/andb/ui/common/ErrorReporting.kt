package ca.stewartclan.andb.ui.common

import android.util.Log
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.stewartclan.andb.R
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A ViewModel whose storage operations report failures to the screen instead of crashing the app. */
abstract class ReportingViewModel : ViewModel() {
    private val _error = MutableStateFlow<String?>(null)

    /** Detail of the last failed operation, until the screen has shown it. */
    val error: StateFlow<String?> = _error.asStateFlow()

    fun errorShown() {
        _error.value = null
    }

    // Last-resort boundary between storage and UI, so any failure is reported rather than fatal.
    @Suppress("TooGenericExceptionCaught")
    protected fun launchReporting(block: suspend () -> Unit): Job = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(javaClass.simpleName, "Operation failed", e)
            _error.value = e.message ?: e.javaClass.simpleName
        }
    }
}

/** Shows [error] (from [ReportingViewModel.error]) in a snackbar, then calls [onShown]. */
@Composable
fun ErrorSnackbar(error: String?, snackbarHostState: SnackbarHostState, onShown: () -> Unit) {
    val message = error?.let { stringResource(R.string.error_operation_failed, it) }
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onShown()
        }
    }
}
