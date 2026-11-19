package app.spliitclient.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.spliitclient.ui.expense.ExpenseFormScreen
import app.spliitclient.ui.expense.ExpenseRoute
import app.spliitclient.ui.group.GroupScreen
import app.spliitclient.ui.groupform.GroupFormRoute
import app.spliitclient.ui.groupform.GroupFormScreen
import app.spliitclient.ui.home.HomeScreen
import app.spliitclient.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlin.math.roundToLong

@Serializable
data object HomeRoute

@Serializable
data class GroupRoute(val groupId: String)

@Serializable
data object SettingsRoute

private fun NavHostController.isIdle(): Boolean =
    currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED

/** Evita il doppio "indietro" durante le animazioni (che svuoterebbe lo stack). */
private fun NavHostController.safePop() {
    if (isIdle()) popBackStack()
}

/** Evita di aprire due volte la stessa schermata con un doppio tocco. */
private fun NavHostController.go(route: Any) {
    if (isIdle()) navigate(route)
}

@Composable
fun AppNav(pendingGroup: StateFlow<String?>, onPendingConsumed: () -> Unit) {
    val nav = rememberNavController()
    val pending by pendingGroup.collectAsStateWithLifecycle()

    LaunchedEffect(pending) {
        val id = pending ?: return@LaunchedEffect
        // Il gruppo viene salvato solo se esiste davvero (lo verifica la schermata del gruppo).
        nav.navigate(GroupRoute(id)) { popUpTo<HomeRoute> { inclusive = false } }
        onPendingConsumed()
    }

    NavHost(
        navController = nav,
        startDestination = HomeRoute,
        enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 6 } },
        exitTransition = { fadeOut(tween(120)) },
        popEnterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(260)) { -it / 6 } },
        popExitTransition = { fadeOut(tween(150)) + slideOutHorizontally(tween(220)) { it / 6 } },
    ) {
        composable<HomeRoute> {
            HomeScreen(
                onOpenGroup = { nav.go(GroupRoute(it)) },
                onCreateGroup = { nav.go(GroupFormRoute()) },
                onSettings = { nav.go(SettingsRoute) },
            )
        }
        composable<GroupRoute> { entry ->
            val route = entry.toRoute<GroupRoute>()
            GroupScreen(
                groupId = route.groupId,
                onBack = { nav.safePop() },
                onAddExpense = { nav.go(ExpenseRoute(route.groupId)) },
                onOpenExpense = { nav.go(ExpenseRoute(route.groupId, expenseId = it)) },
                onReimburse = { r ->
                    nav.go(
                        ExpenseRoute(
                            groupId = route.groupId,
                            reimburseFrom = r.from,
                            reimburseTo = r.to,
                            reimburseAmount = r.amount.roundToLong(),
                        ),
                    )
                },
                onEditGroup = { nav.go(GroupFormRoute(route.groupId)) },
            )
        }
        composable<ExpenseRoute> { entry ->
            ExpenseFormScreen(route = entry.toRoute(), onDone = { nav.safePop() })
        }
        composable<GroupFormRoute> { entry ->
            val route = entry.toRoute<GroupFormRoute>()
            GroupFormScreen(
                groupId = route.groupId,
                onDone = { savedId ->
                    if (route.groupId == null && savedId != null) {
                        nav.navigate(GroupRoute(savedId)) {
                            popUpTo<GroupFormRoute> { inclusive = true }
                        }
                    } else {
                        nav.safePop()
                    }
                },
                onRemoved = { nav.popBackStack<HomeRoute>(inclusive = false) },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(onBack = { nav.safePop() })
        }
    }
}
