package ca.stewartclan.andb.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ca.stewartclan.andb.ui.chooser.ChooserRoute
import ca.stewartclan.andb.ui.chooser.ChooserScreen

@Composable
fun AnDbNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = ChooserRoute) {
        composable<ChooserRoute> {
            ChooserScreen(onNewDatabase = { /* Design view not built yet. */ })
        }
    }
}
