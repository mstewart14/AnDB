package ca.stewartclan.andb.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ca.stewartclan.andb.ui.chooser.ChooserRoute
import ca.stewartclan.andb.ui.chooser.ChooserScreen
import ca.stewartclan.andb.ui.design.DesignRoute
import ca.stewartclan.andb.ui.design.DesignScreen

@Composable
fun AnDbNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = ChooserRoute) {
        composable<ChooserRoute> {
            // Opens the design until the list view exists.
            ChooserScreen(onOpenDatabase = { navController.navigate(DesignRoute(it)) })
        }
        composable<DesignRoute> {
            DesignScreen(onBack = { navController.popBackStack() })
        }
    }
}
