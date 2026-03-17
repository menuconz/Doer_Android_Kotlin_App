package nz.co.doer.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import nz.co.doer.R
import nz.co.doer.data.local.SecureStorageManager

@Composable
fun LoadingScreen(
    secureStorageManager: SecureStorageManager,
    onLoggedIn: () -> Unit,
    onNotLoggedIn: () -> Unit
) {
    LaunchedEffect(Unit) {
        if (secureStorageManager.isLoggedIn) {
            onLoggedIn()
        } else {
            onNotLoggedIn()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.doer_logo),
            contentDescription = "Doer Logo",
            modifier = Modifier.size(120.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}
