package org.sih.itantra.presentation

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.remember
import org.sih.itantra.core.contact.ContactRepository
import org.sih.itantra.presentation.screens.ContactsScreen
import org.sih.itantra.presentation.theme.ITantraTheme

/**
 * Isolated test & launch activity for Tactical Contacts.
 * Allows independent physical testing without modifying shared MainActivity navigation.
 */
class ContactsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ITantraTheme(darkTheme = true) {
                val repository = remember { ContactRepository(applicationContext) }

                ContactsScreen(
                    contactRepository = repository,
                    onOpenChat = { nodeId ->
                        Toast.makeText(this, "Routing to Chat: Node #$nodeId", Toast.LENGTH_SHORT).show()
                    },
                    onBack = {
                        finish()
                    }
                )
            }
        }
    }
}
