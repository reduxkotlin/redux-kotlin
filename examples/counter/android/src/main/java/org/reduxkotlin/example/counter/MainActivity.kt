package org.reduxkotlin.example.counter

import android.os.Bundle
import android.os.Handler
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import org.reduxkotlin.StoreSubscription
import org.reduxkotlin.example.counter.databinding.ActivityMainBinding
import org.reduxkotlin.examples.counter.Decrement
import org.reduxkotlin.examples.counter.Increment
import org.reduxkotlin.examples.counter.reducer
import org.reduxkotlin.threadsafe.createThreadSafeStore

/**
 * This is a sample of basic redux behavior.
 * This is NOT best practice for structuring a multiplatform App.
 */

val store = createThreadSafeStore(reducer, 0)

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
            1000
        )
    }
}
