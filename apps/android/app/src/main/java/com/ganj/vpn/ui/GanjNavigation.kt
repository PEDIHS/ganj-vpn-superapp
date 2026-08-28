package com.ganj.vpn.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ganj.vpn.R

internal enum class GanjDestination(@StringRes val labelRes: Int) {
    Home(R.string.nav_home),
    Servers(R.string.nav_servers),
    Connect(R.string.nav_connect),
    Store(R.string.nav_store),
    Account(R.string.nav_account),
}

@Composable
internal fun GanjLiquidBottomNavigation(
    selectedDestination: GanjDestination,
    onDestinationSelected: (GanjDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    GanjGlassSurface(
        role = GanjGlassRole.Regular,
        accent = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth(),
        shapeRadius = 30.dp,
        padding = PaddingValues(horizontal = 8.dp, vertical = 7.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            GanjDestination.entries.forEach { destination ->
                val selected = destination == selectedDestination
                val label = stringResource(destination.labelRes)
                val description = UiAccessibilityPolicy.destinationDescription(
                    label = label,
                    selected = selected,
                    selectedSuffix = stringResource(R.string.a11y_selected),
                )
                val container = when {
                    selected && destination == GanjDestination.Connect -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                    selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    else -> Color.Transparent
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = UiAccessibilityPolicy.MinimumTouchTargetDp.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(container)
                        .semantics {
                            this.selected = selected
                            contentDescription = description
                        }
                        .clickable(
                            role = Role.Tab,
                            onClick = { onDestinationSelected(destination) },
                        )
                        .padding(horizontal = 4.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
