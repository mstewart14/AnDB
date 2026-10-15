package ca.stewartclan.andb.ui

import androidx.compose.runtime.Composable
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

@Composable
fun AnDbNavHost() {
    val navController = rememberNavController()
    val back: () -> Unit = { navController.popBackStack() }
    NavHost(navController = navController, startDestination = ChooserRoute) {
        composable<ChooserRoute> {
            ChooserScreen(
                onOpenDatabase = { navController.navigate(RecordListRoute(it)) },
                // Like Pilot-DB, a new database opens in its design; back then leads to its records.
                onCreatedDatabase = {
                    navController.navigate(RecordListRoute(it))
                    navController.navigate(DesignRoute(it))
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
