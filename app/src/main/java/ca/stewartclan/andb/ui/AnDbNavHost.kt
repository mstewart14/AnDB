package ca.stewartclan.andb.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ca.stewartclan.andb.ui.chooser.ChooserRoute
import ca.stewartclan.andb.ui.chooser.ChooserScreen
import ca.stewartclan.andb.ui.design.DesignRoute
import ca.stewartclan.andb.ui.design.DesignScreen
import ca.stewartclan.andb.ui.list.CsvImportRoute
import ca.stewartclan.andb.ui.list.CsvImportScreen
import ca.stewartclan.andb.ui.list.FilterEditorRoute
import ca.stewartclan.andb.ui.list.FilterEditorScreen
import ca.stewartclan.andb.ui.list.RecordListRoute
import ca.stewartclan.andb.ui.list.RecordListScreen
import ca.stewartclan.andb.ui.list.ViewEditorRoute
import ca.stewartclan.andb.ui.list.ViewEditorScreen
import ca.stewartclan.andb.ui.record.NEW_RECORD_ID
import ca.stewartclan.andb.ui.record.RecordRoute
import ca.stewartclan.andb.ui.record.RecordScreen

/**
 * AnDB's screens. A database file [incoming] from another app takes the user to the database list,
 * which offers to add it, then calls [onIncomingHandled].
 */
@Composable
fun AnDbNavHost(incoming: Uri? = null, onIncomingHandled: () -> Unit = {}) {
    val navController = rememberNavController()
    val back: () -> Unit = { navController.popBackStack() }
    LaunchedEffect(incoming) { if (incoming != null) navController.popBackStack(ChooserRoute, inclusive = false) }
    NavHost(navController = navController, startDestination = ChooserRoute) {
        composable<ChooserRoute> {
            ChooserScreen(
                incoming = incoming,
                onIncomingHandled = onIncomingHandled,
                onOpenDatabase = { navController.navigate(RecordListRoute(it)) },
                // Like Pilot-DB, a new database opens in its design; back then leads to its records.
                onCreatedDatabase = {
                    navController.navigate(RecordListRoute(it))
                    navController.navigate(DesignRoute(it))
                },
                // One made from a CSV file gets its fields from the import; back then shows its records.
                onCreatedFromCsv = { db, uri ->
                    navController.navigate(RecordListRoute(db))
                    navController.navigate(CsvImportRoute(db, uri))
                }
            )
        }
        composable<RecordListRoute> {
            RecordListScreen(
                onBack = back,
                onOpenRecord = { db, record -> navController.navigate(RecordRoute(db, record)) },
                onEditDesign = { navController.navigate(DesignRoute(it)) },
                onEditView = { db, view -> navController.navigate(ViewEditorRoute(db, view)) },
                onEditFilter = { db, filter -> navController.navigate(FilterEditorRoute(db, filter)) },
                onImportCsv = { db, uri -> navController.navigate(CsvImportRoute(db, uri)) },
                onFixLink = { db, field -> navController.navigate(DesignRoute(db, field)) }
            )
        }
        composable<CsvImportRoute> { CsvImportScreen(onDone = back) }
        composable<FilterEditorRoute> { FilterEditorScreen(onDone = back) }
        composable<ViewEditorRoute> { ViewEditorScreen(onDone = back) }
        composable<RecordRoute> {
            RecordScreen(
                onBack = back,
                onOpenRecord = { db, record -> navController.navigate(RecordRoute(db, record)) },
                onAddLinkedRecord = { db, field, uri ->
                    navController.navigate(RecordRoute(db, NEW_RECORD_ID, field, uri))
                }
            )
        }
        composable<DesignRoute> { DesignScreen(onBack = back) }
    }
}
