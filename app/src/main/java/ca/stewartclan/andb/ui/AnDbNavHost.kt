package ca.stewartclan.andb.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ca.stewartclan.andb.ui.chooser.ChooserRoute
import ca.stewartclan.andb.ui.chooser.ChooserScreen
import ca.stewartclan.andb.ui.design.DesignRoute
import ca.stewartclan.andb.ui.design.DesignScreen
import ca.stewartclan.andb.ui.list.RecordListRoute
import ca.stewartclan.andb.ui.list.RecordListScreen
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
                onEditDesign = { navController.navigate(DesignRoute(it)) }
            )
        }
        composable<RecordRoute> { RecordScreen(onBack = back) }
        composable<DesignRoute> { DesignScreen(onBack = back) }
    }
}
