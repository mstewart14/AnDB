package ca.stewartclan.andb

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ca.stewartclan.andb.ui.AnDbNavHost
import ca.stewartclan.andb.ui.theme.AnDbTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AnDbTheme {
                AnDbNavHost()
            }
        }
    }
}
