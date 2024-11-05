package com.afkanerd.deku.DefaultSMS

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.afkanerd.deku.DefaultSMS.AdaptersViewModels.ThreadedConversationsViewModel
import com.example.compose.AppTheme
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.preference.PreferenceManager
import com.afkanerd.deku.DefaultSMS.AdaptersViewModels.ConversationsViewModel
import com.afkanerd.deku.DefaultSMS.ui.Conversations
import com.afkanerd.deku.DefaultSMS.ui.ThreadConversationLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
object HomeScreen
@Serializable
object ConversationsScreen

class ThreadsConversationActivity : AppCompatActivity() {

    lateinit var navController: NavHostController


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val viewModel: ThreadedConversationsViewModel by viewModels()
        val conversationViewModel: ConversationsViewModel by viewModels()
        checkLoadNatives(viewModel)

        setContent {
            AppTheme {
                navController = rememberNavController()
                Surface(Modifier.safeDrawingPadding()) {

                    NavHost(
                        modifier = Modifier,
                        navController = navController,
                        startDestination = HomeScreen,
                    ) {
                        composable<HomeScreen>{
                            ThreadConversationLayout(
                                viewModel=viewModel,
                                conversationsViewModel=conversationViewModel,
                                navController
                            )
                        }

                        composable<ConversationsScreen>{
                            Conversations(
                                viewModel=conversationViewModel,
                                navController=navController
                            )
                        }
                    }
                }
            }
        }
    }

    private fun checkLoadNatives(viewModel: ThreadedConversationsViewModel) {
        if(PreferenceManager.getDefaultSharedPreferences(applicationContext)
                .getBoolean(getString(R.string.configs_load_natives), false)){
            CoroutineScope(Dispatchers.Default).launch {
                viewModel.reset(applicationContext)
                PreferenceManager.getDefaultSharedPreferences(applicationContext).edit()
                    .putBoolean(getString(R.string.configs_load_natives), true).apply()
            }
        }

    }
}