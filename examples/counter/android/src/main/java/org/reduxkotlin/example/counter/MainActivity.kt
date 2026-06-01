package org.reduxkotlin.example.counter

import android.os.Bundle
import android.os.Handler
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import org.reduxkotlin.StoreSubscription
import org.reduxkotlin.devtools.DevToolsConfig
import org.reduxkotlin.devtools.devTools
import org.reduxkotlin.example.counter.databinding.ActivityMainBinding
import org.reduxkotlin.examples.counter.Decrement
import org.reduxkotlin.examples.counter.Increment
import org.reduxkotlin.examples.counter.reducer
import org.reduxkotlin.threadsafe.createThreadSafeStore

/**
 * This is a sample of basic redux behavior.
 * This is NOT best practice for structuring a multiplatform App.
 *
 * Redux DevTools is enabled below. To watch actions/state live:
 *   1. On your dev machine run:  npx @redux-devtools/cli --open   (server + UI on :8000)
 *   2. Run this app on an emulator (host 10.0.2.2 reaches your machine), or on a
 *      USB device after `adb reverse tcp:8000 tcp:8000` (then set host = "localhost").
 */

val store = createThreadSafeStore(
    reducer,
    0,
    devTools(
        DevToolsConfig(
            name = "Counter",
            host = "10.0.2.2",
            logger = { Log.d("DevTools", it) },
        ),
    ),
)

class MainActivity : AppCompatActivity() {
    private lateinit var storeSubscription: StoreSubscription
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            v.updatePadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        storeSubscription = store.subscribe { render(store.state) }
        binding.btnIncrement.setOnClickListener { store.dispatch(Increment()) }
        binding.btnDecrement.setOnClickListener { store.dispatch(Decrement()) }
        binding.btnAsync.setOnClickListener { incrementAsync() }
        binding.btnIncrementIfOdd.setOnClickListener { incrementIfOdd() }
    }

    private fun render(state: Int) {
        binding.txtLabel.text = "Clicked: $state times"
    }

    private fun incrementIfOdd() {
        if (store.state % 2 != 0) {
            store.dispatch(Increment())
        }
    }

    private fun incrementAsync() {
        Handler(mainLooper).postDelayed(
            {
                store.dispatch(Increment())
            },
            1000,
        )
    }
}
