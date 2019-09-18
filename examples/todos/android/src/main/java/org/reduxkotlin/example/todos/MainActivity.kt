package org.reduxkotlin.example.todos

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.activity_main.*
import org.reduxkotlin.StoreSubscription
import org.reduxkotlin.createStore
import org.reduxkotlin.examples.todos.AppState
import org.reduxkotlin.examples.todos.rootReducer

/**
 * This is a sample of basic redux behavior.
 * This is NOT best practice for structuring a multiplatform App.
 */


val store = createStore(rootReducer, AppState())

class MainActivity: AppCompatActivity() {
    lateinit var storeSubscription: StoreSubscription
    var adapter: Adapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        storeSubscription = store.subscribe { render(store.state) }
//        btn_increment.setOnClickListener { store.dispatch(Increment()) }
//        btn_decrement.setOnClickListener { store.dispatch(Decrement()) }
    }

    private fun render(state: AppState) {
    }
}