package com.happyfamily.contract

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.happyfamily.contract.ui.AppNavigation
import com.happyfamily.contract.ui.theme.ContractTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ContractTheme {
                AppNavigation()
            }
        }
    }
}
