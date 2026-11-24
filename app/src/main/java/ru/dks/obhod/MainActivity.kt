package ru.dks.obhod

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ru.dks.obhod.ui.DataTransferScreen
import ru.dks.obhod.ui.GpaScreen
import ru.dks.obhod.ui.HangarScreen
import ru.dks.obhod.ui.HomeScreen
import ru.dks.obhod.ui.PositionRemarksScreen
import ru.dks.obhod.ui.RemarkEditScreen
import ru.dks.obhod.ui.theme.DksTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DksTheme {
                val nav = rememberNavController()
                NavHost(nav, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            onOpen = { nav.navigate("gpa/$it") },
                            onTransfer = { nav.navigate("transfer") },
                            onPosition = { nav.navigate("position/$it") },
                        )
                    }
                    composable("transfer") {
                        DataTransferScreen(onBack = { nav.popBackStack() })
                    }
                    composable(
                        "gpa/{gpaId}?tab={tab}",
                        arguments = listOf(
                            navArgument("gpaId") { type = NavType.StringType },
                            navArgument("tab") { type = NavType.IntType; defaultValue = 0 },
                        ),
                    ) { e ->
                        val id = e.arguments!!.getString("gpaId")!!
                        GpaScreen(
                            gpaId = id,
                            initialTab = e.arguments!!.getInt("tab"),
                            onBack = { nav.popBackStack() },
                            onHangar = { nav.navigate("hangar/$id") },
                            onRemark = { remarkId -> nav.navigate("remark/$id/$remarkId") },
                        )
                    }
                    composable("hangar/{gpaId}") { e ->
                        HangarScreen(gpaId = e.arguments!!.getString("gpaId")!!, onBack = { nav.popBackStack() })
                    }
                    composable("position/{positionId}") { e ->
                        val positionId = e.arguments!!.getString("positionId")!!
                        PositionRemarksScreen(
                            positionId = positionId,
                            onBack = { nav.popBackStack() },
                            onRemark = { remarkId -> nav.navigate("remark/$positionId/$remarkId") },
                        )
                    }
                    composable(
                        "remark/{gpaId}/{remarkId}",
                        arguments = listOf(
                            navArgument("gpaId") { type = NavType.StringType },
                            navArgument("remarkId") { type = NavType.LongType },
                        ),
                    ) { e ->
                        RemarkEditScreen(
                            gpaId = e.arguments!!.getString("gpaId")!!,
                            remarkId = e.arguments!!.getLong("remarkId"),
                            onBack = { nav.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}
