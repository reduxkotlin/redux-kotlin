package org.reduxkotlin.example.counter

import android.os.Bundle
import android.os.Handler
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import org.reduxkotlin.StoreSubscription
import org.reduxkotlin.createThreadSafeStore
import org.reduxkotlin.examples.counter.Decrement
import org.reduxkotlin.examples.counter.Increment
import org.reduxkotlin.examples.counter.reducer

/**
 * This is a sample of basic redux behavior.
 * This is NOT best practice for structuring a multiplatform App.
 */

val store = createThreadSafeStore(reducer, 0)

class MainActivity : AppCompatActivity() {
  lateinit var storeSubscription: StoreSubscription

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    storeSubscription = store.subscribe { render(store.state) }
    btn_increment.setOnClickListener { store.dispatch(Increment()) }
    btn_decrement.setOnClickListener { store.dispatch(Decrement()) }
    btn_async.setOnClickListener { incrementAsync() }
    btn_increment_if_odd.setOnClickListener { incrementIfOdd() }
  }

  private fun render(state: Int) {
    txt_label.text = "Clicked: $state times"
  }

  private fun incrementIfOdd() {
    if (store.state % 2 != 0) {
      store.dispatch(Increment())
    }
  }

  private fun incrementAsync() {
    Handler().postDelayed(
      {
        store.dispatch(Increment())
      },
      1000
    )
  }
}
