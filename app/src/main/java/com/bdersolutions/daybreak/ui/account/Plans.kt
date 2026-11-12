package com.bdersolutions.daybreak.ui.account

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bdersolutions.daybreak.R
import com.bdersolutions.daybreak.data.account.AccountError
import com.bdersolutions.daybreak.data.account.Access
import com.bdersolutions.daybreak.data.account.ApiResult
import com.bdersolutions.daybreak.data.account.Entitlement
import com.bdersolutions.daybreak.data.account.Plan
import com.bdersolutions.daybreak.data.haptics.HapticEvent
import com.bdersolutions.daybreak.ui.components.Button
import com.bdersolutions.daybreak.ui.components.FilledTonalButton
import com.bdersolutions.daybreak.ui.components.Surface
import com.bdersolutions.daybreak.ui.components.TextButton
import com.bdersolutions.daybreak.ui.haptics.LocalHaptics
import kotlinx.coroutines.launch

/** What the signed-in person may use. Provided once at the top of the app (see DaybreakApp). */
val LocalAccess = compositionLocalOf { Access.NONE }

/**
 * Opens the plans sheet for a locked feature (or without one). Any locked row calls this with its
 * [Entitlement]; the sheet then says which plan it is part of.
 */
fun interface PlansLauncher {
    fun show(feature: Entitlement?)
}

val LocalPlans = staticCompositionLocalOf { PlansLauncher { } }

/** Whether the person may use [this] right now: the one place the screens ask. */
@Composable
fun Entitlement.allowed(): Boolean = LocalAccess.current.allows(this)

/** A locked row is dimmed (animated, so it does not jump when the plan changes). */
@Composable
fun Modifier.lockedAlpha(locked: Boolean): Modifier {
    val alpha by animateFloatAsState(if (locked) 0.6f else 1f, label = "lockedAlpha")
    return this.alpha(alpha)
}

/** The lock that sits at the end of a dimmed row. */
@Composable
fun LockIcon(modifier: Modifier = Modifier) {
    Icon(
        Icons.Outlined.Lock,
        contentDescription = stringResource(R.string.v2_locked),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
fun Entitlement.label(): String = stringResource(
    when (this) {
        Entitlement.APP_NAME -> R.string.v2_feature_app_name
        Entitlement.APP_ICON -> R.string.v2_feature_app_icon
        Entitlement.CALENDAR_EXPORT -> R.string.v2_feature_calendar_export
        Entitlement.WIDGET_CUSTOMISATION -> R.string.v2_feature_widget
        Entitlement.SHARE_PICTURE -> R.string.v2_feature_share_picture
        Entitlement.SYNC -> R.string.v2_feature_sync
        Entitlement.DAWN_REASONING -> R.string.v2_feature_dawn_reasoning
        Entitlement.DAWN_DRAFTS -> R.string.v2_feature_dawn_drafts
        Entitlement.HAPTICS_STUDIO -> R.string.v2_feature_haptics
        Entitlement.ACCENT_PALETTES -> R.string.v2_feature_accent
        Entitlement.AMOLED -> R.string.v2_feature_amoled
    },
)

@Composable
fun Plan.label(): String = stringResource(
    when (this) {
        Plan.FREE -> R.string.v2_plan_free
        Plan.PLUS -> R.string.v2_plan_plus
        Plan.PRO -> R.string.v2_plan_pro
    },
)

/** Opens [url] in the browser. Does nothing if the phone has none. */
fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No browser installed: nothing to open.
    }
}

@Composable
fun AccountError.text(): String = stringResource(
    when (this) {
        AccountError.OFFLINE -> R.string.v2_error_offline
        AccountError.UNAUTHORIZED -> R.string.v2_error_unauthorized
        AccountError.UNKNOWN_CODE -> R.string.v2_error_unknown_code
        AccountError.LIMIT -> R.string.v2_error_limit
        AccountError.DUPLICATE -> R.string.v2_error_duplicate
        AccountError.NOT_OFFERED -> R.string.v2_error_not_offered
        AccountError.NO_SUBSCRIPTION -> R.string.v2_error_no_subscription
        AccountError.RATE_LIMITED -> R.string.v2_error_rate_limited
        AccountError.BAD_REQUEST, AccountError.UNAVAILABLE -> R.string.v2_error_unavailable
    },
)

/**
 * The plans sheet a locked row opens. It says which plan the feature is part of and lets a signed-in person go
 * to Stripe Checkout in the browser ([onCheckout] returns the address); without an account it leads to the
 * account page first. "All plans" opens the plans page of the website ([plansPageUrl]).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlansSheet(
    feature: Entitlement?,
    access: Access,
    signedIn: Boolean,
    plansPageUrl: String?,
    onCheckout: suspend (Plan) -> ApiResult<String>,
    onPortal: suspend () -> ApiResult<String>,
    onOpenAccount: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptics = LocalHaptics.current
    var busy by remember { mutableStateOf<Plan?>(null) }
    var error by remember { mutableStateOf<AccountError?>(null) }
    val needed = feature?.minPlan

    fun closeThen(action: () -> Unit) {
        scope.launch {
            sheetState.hide()
            action()
        }
    }

    fun choose(plan: Plan) {
        if (busy != null) return
        haptics.perform(HapticEvent.TAP)
        if (!signedIn) {
            closeThen(onOpenAccount)
            return
        }
        busy = plan
        error = null
        scope.launch {
            when (val result = onCheckout(plan)) {
                is ApiResult.Ok -> {
                    busy = null
                    openUrl(context, result.value)
                }

                is ApiResult.Fail -> {
                    busy = null
                    error = result.error
                    haptics.perform(HapticEvent.ERROR)
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.WorkspacePremium,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Text(
                    text = if (feature != null && needed != null) {
                        stringResource(R.string.v2_plans_title_feature, feature.label(), needed.label())
                    } else {
                        stringResource(R.string.v2_plans_title)
                    },
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.v2_plans_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            val showPlus = access.plan == Plan.FREE && (needed == null || needed == Plan.PLUS)
            if (showPlus) {
                PlanCard(
                    plan = Plan.PLUS,
                    points = listOf(R.string.v2_plus_1, R.string.v2_plus_2, R.string.v2_plus_3, R.string.v2_plus_4),
                    highlighted = needed == Plan.PLUS,
                    busy = busy == Plan.PLUS,
                    enabled = busy == null,
                    onChoose = { choose(Plan.PLUS) },
                )
                Spacer(Modifier.height(10.dp))
            }
            if (access.plan != Plan.PRO) {
                PlanCard(
                    plan = Plan.PRO,
                    points = listOf(R.string.v2_pro_1, R.string.v2_pro_2, R.string.v2_pro_3, R.string.v2_pro_4),
                    highlighted = needed == Plan.PRO || needed == null,
                    busy = busy == Plan.PRO,
                    enabled = busy == null,
                    onChoose = { choose(Plan.PRO) },
                )
            }
            AnimatedVisibility(
                visible = error != null,
                enter = fadeIn() + expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
                exit = fadeOut() + shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
            ) {
                Text(
                    text = error?.text().orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            if (!signedIn) {
                Text(
                    text = stringResource(R.string.v2_plans_needs_account),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            if (plansPageUrl != null) {
                TextButton(onClick = { openUrl(context, plansPageUrl) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.v2_plans_all))
                }
            }
            if (signedIn && access.plan != Plan.FREE) {
                TextButton(
                    onClick = {
                        scope.launch {
                            when (val result = onPortal()) {
                                is ApiResult.Ok -> openUrl(context, result.value)
                                is ApiResult.Fail -> {
                                    error = result.error
                                    haptics.perform(HapticEvent.ERROR)
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.v2_plans_manage))
                }
            }
            TextButton(onClick = { closeThen(onDismiss) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.v2_plans_close))
            }
        }
    }
}

@Composable
private fun PlanCard(
    plan: Plan,
    points: List<Int>,
    highlighted: Boolean,
    busy: Boolean,
    enabled: Boolean,
    onChoose: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (highlighted) scheme.primaryContainer.copy(alpha = 0.45f) else scheme.surfaceContainer,
        border = BorderStroke(1.dp, if (highlighted) scheme.primary.copy(alpha = 0.6f) else scheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = plan.label(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            points.forEach { point ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(16.dp),
                    )
                    Text(
                        text = stringResource(point),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurface,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            val buttonModifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
            val content: @Composable () -> Unit = {
                if (busy) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                } else {
                    Text(stringResource(R.string.v2_plans_choose, plan.label()))
                }
            }
            if (highlighted) {
                Button(onClick = onChoose, enabled = enabled, shape = CircleShape, modifier = buttonModifier) { content() }
            } else {
                FilledTonalButton(onClick = onChoose, enabled = enabled, shape = CircleShape, modifier = buttonModifier) { content() }
            }
        }
    }
}

/**
 * Wraps a part of a screen that belongs to a plan. Where the plan has it, the content is shown as it is.
 * Where it does not, the content is dimmed (animated), cannot be used, and a tap anywhere on it opens the
 * plans sheet for [entitlement]. A small lock marks the corner so the dimming is not mistaken for "off".
 */
@Composable
fun LockedArea(
    entitlement: Entitlement,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val allowed = entitlement.allowed()
    if (allowed) {
        Box(modifier = modifier) { content() }
        return
    }
    val plans = LocalPlans.current
    val haptics = LocalHaptics.current
    Box(modifier = modifier) {
        Box(modifier = Modifier.lockedAlpha(true)) { content() }
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClickLabel = stringResource(R.string.v2_locked_plan, entitlement.minPlan.label()),
                ) {
                    haptics.perform(HapticEvent.TAP)
                    plans.show(entitlement)
                },
        )
        LockIcon(modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(18.dp))
    }
}
