package it.polito.mad.g28.tymes

import android.content.Intent
import android.content.IntentSender
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ktx.database
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase


class MainActivity : AppCompatActivity() {

    private lateinit var toggle : ActionBarDrawerToggle
    private lateinit var drawerLayout: DrawerLayout
    private var oneTapClient: SignInClient? = null
    private var signInRequest: BeginSignInRequest? = null
    private val REQ_ONE_TAP: Int = 1337
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Set Google Auth
        auth = Firebase.auth

        oneTapClient = Identity.getSignInClient(this)
        signInRequest = BeginSignInRequest.builder()
            .setPasswordRequestOptions(
                BeginSignInRequest.PasswordRequestOptions.builder()
                .setSupported(true)
                .build())
            .setGoogleIdTokenRequestOptions(
                BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                .setSupported(true)
                // Your server's client ID, not your Android client ID.
                .setServerClientId("912277110592-3oo1mgepmkfjmr8qde7cjhe1coo8fvrf.apps.googleusercontent.com")
                // Only show accounts previously used to sign in.
                .setFilterByAuthorizedAccounts(false)
                .build())
            // Automatically sign in when exactly one credential is retrieved.
            .setAutoSelectEnabled(true)
            .build()


        oneTapClient!!.beginSignIn(signInRequest!!)
            .addOnSuccessListener(this) { result ->
                try {
                    Log.d("lifecycle", "success listener")
                    startIntentSenderForResult(
                        result.pendingIntent.intentSender, REQ_ONE_TAP,
                        null, 0, 0, 0, null)
                } catch (e: IntentSender.SendIntentException) {
                    Log.e("Sign In", "Couldn't start One Tap UI: ${e.localizedMessage}")
                }
            }
            .addOnFailureListener(this) { e ->
                // No saved credentials found. Launch the One Tap sign-up flow, or
                // do nothing and continue presenting the signed-out UI.
                Log.d("Sign In", e.localizedMessage)
            }

        drawerLayout = findViewById(R.id.drawerlayout)
        val navView: NavigationView = findViewById(R.id.navigationView)

        toggle = ActionBarDrawerToggle(this, drawerLayout, R.string.nav_open, R.string.nav_close)

        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)



        navView.setNavigationItemSelectedListener {

            it.isChecked = true

            when(it.itemId){

                R.id.home -> changeFrag(Home(), it.title.toString())
                R.id.my_profile_icon -> changeFrag(ShowProfileActivity(), it.title.toString())
                R.id.my_clan_icon -> changeFrag(clanFragement(), it.title.toString())
                R.id.all_tslots_list_icon -> changeFrag(TimeSlotListFragment(), it.title.toString())
                R.id.my_tslots_icon -> changeFrag(TimeSlotDetailsFragment(), it.title.toString())
                R.id.tymes_settings_icon -> changeFrag(SettingsFragment(), it.title.toString())
                R.id.about_tymes_icon -> changeFrag(aboutFragment(), it.title.toString())

            }
            true
        }


    }

    override fun onStart() {
        super.onStart()
        // Check if user is signed in (non-null) and update UI accordingly.
        val currentUser = auth.currentUser
        updateDB(currentUser)
    }

    private fun updateDB(currentUser: FirebaseUser?) {
        // Add user to the Firebase DB
        if (currentUser != null){
            Log.d("lifecycle", "update UI")
            val database = Firebase.firestore
            val name = currentUser.displayName
            val email = currentUser.email
            val uid = currentUser.uid

            val user = User(uid,name, null, null, null, null, null, email, null)
            database.collection("users").document(uid).set(user)
                .addOnSuccessListener {Log.d("lifecycle", "successfully added user with uid: ${uid}")}
        }
    }

    private fun changeFrag(fragment: Fragment, title: String){
        val fragmentManager = supportFragmentManager
        val fragmentTransaction = fragmentManager.beginTransaction()
        fragmentTransaction.replace(R.id.fragmentContainerView, fragment).commit()
        drawerLayout.closeDrawers()
        setTitle(title)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (toggle.onOptionsItemSelected(item)){
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQ_ONE_TAP -> {
                try {
                    val credential = oneTapClient!!.getSignInCredentialFromIntent(data)
                    val idToken = credential.googleIdToken
                    val username = credential.id
                    val password = credential.password
                    when {
                        idToken != null -> {
                            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                            auth.signInWithCredential(firebaseCredential)
                                .addOnCompleteListener(this) { task ->
                                    if (task.isSuccessful) {
                                        // Sign in success, update UI with the signed-in user's information
                                        Log.d("GoogleAuth", "signInWithCredential:success")
                                        val user = auth.currentUser
                                        updateDB(user)
                                        Log.d("GoogleAuth", "updated UI")
                                    } else {
                                        // If sign in fails, display a message to the user.
                                        Log.d("GoogleAuth", "signInWithCredential:failure", task.exception)
                                        updateDB(null)
                                    }
                                }

                            Log.d("GoogleAuth", "Got ID token.")
                        }
                        password != null -> {
                            // Got a saved username and password. Use them to authenticate
                            // with your backend.
                            Log.d("Auth", "Got password.")
                        }
                        else -> {
                            // Shouldn't happen.
                            Log.d("Auth", "No ID token or password!")
                        }
                    }
                } catch (e: ApiException) {
                    when (e.statusCode) {
                        CommonStatusCodes.CANCELED -> {
                            Log.d("GoogleAuth", "One-tap dialog was closed.")
                            // Don't re-prompt the user.
                            var showOneTapUI = false
                        }
                        CommonStatusCodes.NETWORK_ERROR -> {
                            Log.d("GoogleAuth", "One-tap encountered a network error.")
                            // Try again or just ignore.
                        }
                        else -> {
                            Log.d("GoogleAuth", "Couldn't get credential from result." +
                                    " (${e.localizedMessage})")
                        }
                    }

                }
            }
        }
    }

}